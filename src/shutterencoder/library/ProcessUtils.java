package shutterencoder.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility helpers for starting and managing external processes safely.
 */
public class ProcessUtils {
    private static final Logger logger = Logger.getLogger(ProcessUtils.class.getName());

    public static final long DEFAULT_SHORT = 30L; // seconds
    public static final long DEFAULT_LONG = 120L; // seconds

    /**
     * Run a command, merge stderr into stdout, capture output and enforce a timeout.
     * Throws TimeoutException if the process does not finish in time.
     */
    public static String runAndCapture(List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException, TimeoutException {

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> reader = executor.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading process output", e);
            }
        });

        boolean finished = false;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            try {
                // Allow reader to finish flushing a bit after process exits
                reader.get(Math.max(2, Math.min(timeoutSeconds, 5)), TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                logger.log(Level.WARNING, "Reader task failed", e);
            }
        } catch (TimeoutException te) {
            logger.log(Level.WARNING, "Process timed out, forcing kill");
            safeDestroy(process, 5);
            throw te;
        } finally {
            executor.shutdownNow();
        }

        if (!finished) {
            safeDestroy(process, 5);
            throw new TimeoutException("Process timed out after " + timeoutSeconds + " seconds");
        }

        int exit = process.exitValue();
        if (exit != 0) {
            logger.log(Level.INFO, "Process exited with non-zero code: " + exit);
        }

        return output.toString();
    }

    public static class StartedProcess {
        public final Process process;
        public final ExecutorService executor;
        public final Future<?> readerFuture;

        public StartedProcess(Process p, ExecutorService e, Future<?> f) {
            this.process = p;
            this.executor = e;
            this.readerFuture = f;
        }
    }

    /**
     * Start a long-lived process and stream merged output to the provided consumer.
     * Caller should call {@link #stopStartedProcess(StartedProcess, long)} to stop and cleanup.
     */
    public static StartedProcess startProcessWithMergedOutput(List<String> command, Consumer<String> lineConsumer)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> reader = executor.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (lineConsumer != null) {
                        try {
                            lineConsumer.accept(line);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Line consumer threw", e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading process output", e);
            }
        });

        return new StartedProcess(process, executor, reader);
    }

    /**
     * Stop a started process, waiting up to waitSeconds before forcing a kill.
     */
    public static void stopStartedProcess(StartedProcess sp, long waitSeconds) {
        if (sp == null) return;
        try {
            boolean finished = sp.process.waitFor(waitSeconds, TimeUnit.SECONDS);
            if (!finished) {
                logger.log(Level.WARNING, "Process did not exit in time, destroying forcibly");
                sp.process.destroyForcibly();
                sp.process.waitFor(5, TimeUnit.SECONDS);
            }

            try {
                sp.readerFuture.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // best-effort
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sp.executor.shutdownNow();
        }
    }

    /**
     * Safely destroy a process: request destroy, wait, then force kill.
     */
    public static void safeDestroy(Process process, long waitSeconds) {
        if (process == null) return;
        if (!process.isAlive()) return;
        try {
            process.destroy();
            if (!process.waitFor(waitSeconds, TimeUnit.SECONDS)) {
                logger.log(Level.WARNING, "Forcing process kill");
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
