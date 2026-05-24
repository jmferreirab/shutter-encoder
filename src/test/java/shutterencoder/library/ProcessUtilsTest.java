package shutterencoder.library;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class ProcessUtilsTest {

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Test
    public void testRunAndCaptureEcho() throws Exception {
        List<String> cmd;
        if (isWindows()) {
            cmd = Arrays.asList("cmd", "/c", "echo hello");
        } else {
            cmd = Arrays.asList("sh", "-c", "echo hello");
        }

        String out = ProcessUtils.runAndCapture(cmd, ProcessUtils.DEFAULT_SHORT);
        assertTrue(out.toLowerCase().contains("hello"));
    }

    @Test
    public void testTimeout() throws Exception {
        List<String> cmd;
        if (isWindows()) {
            // ping with count approximates a sleep on Windows
            cmd = Arrays.asList("cmd", "/c", "ping -n 6 127.0.0.1 > nul && echo done");
        } else {
            cmd = Arrays.asList("sh", "-c", "sleep 5; echo done");
        }

        assertThrows(java.util.concurrent.TimeoutException.class, () -> {
            ProcessUtils.runAndCapture(cmd, 1);
        });
    }
}
