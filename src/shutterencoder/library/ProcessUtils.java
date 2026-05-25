package shutterencoder.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ProcessUtils {

    public static class ProcessWrapper {
        public final Process process;
        private final Thread stdoutThread;
        private final Thread stderrThread;

        ProcessWrapper(Process process, Thread stdoutThread, Thread stderrThread) {
            this.process = process;
            this.stdoutThread = stdoutThread;
            this.stderrThread = stderrThread;
        }

        public int waitForAndCleanup() throws InterruptedException {
            int rc = -1;
            try {
                rc = process.waitFor();
            } finally {
                // reader threads use try-with-resources and will exit when streams close
                if (stdoutThread != null && stdoutThread.isAlive()) {
                    try { stdoutThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                if (stderrThread != null && stderrThread.isAlive()) {
                    try { stderrThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                // close any remaining streams
                try { process.getInputStream().close(); } catch (IOException ignored) {}
                try { process.getErrorStream().close(); } catch (IOException ignored) {}
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
            }
            return rc;
        }

        public void destroyAndCleanup() {
            try { process.destroy(); } catch (Exception ignored) {}
            try {
                if (stdoutThread != null && stdoutThread.isAlive()) stdoutThread.join();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try {
                if (stderrThread != null && stderrThread.isAlive()) stderrThread.join();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { process.getInputStream().close(); } catch (IOException ignored) {}
            try { process.getErrorStream().close(); } catch (IOException ignored) {}
            try { process.getOutputStream().close(); } catch (IOException ignored) {}
        }
    }

    public static ProcessWrapper startProcess(List<String> command, Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer) throws IOException {
        Objects.requireNonNull(command, "command");
        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();

        Thread out = consumeStream(p.getInputStream(), stdoutConsumer);
        Thread err = consumeStream(p.getErrorStream(), stderrConsumer);

        if (out != null) out.start();
        if (err != null) err.start();

        return new ProcessWrapper(p, out, err);
    }

    public static ProcessWrapper startShellCommand(String command, Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer) throws IOException {
        Objects.requireNonNull(command, "command");
        String os = System.getProperty("os.name");
        ProcessBuilder pb;
        if (os.contains("Windows")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("/bin/bash", "-c", command);
        }

        Process p = pb.start();

        Thread out = consumeStream(p.getInputStream(), stdoutConsumer);
        Thread err = consumeStream(p.getErrorStream(), stderrConsumer);

        if (out != null) out.start();
        if (err != null) err.start();

        return new ProcessWrapper(p, out, err);
    }

    private static Thread consumeStream(final InputStream in, final Consumer<String> lineConsumer) {
        if (in == null || lineConsumer == null) return null;

        return new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    try { lineConsumer.accept(l); } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {
            }
        }, "ProcessUtils-stream-consumer");
    }

}
