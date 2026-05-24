package shutterencoder.library;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

public class ProcessUtilsTest {

    @Test
    public void testRunAndCaptureEcho() throws Exception {
        String out = ProcessUtils.runAndCapture(Arrays.asList("sh", "-c", "echo hello"), ProcessUtils.DEFAULT_SHORT);
        assertTrue(out.contains("hello"));
    }

    @Test
    public void testTimeout() throws Exception {
        assertThrows(java.util.concurrent.TimeoutException.class, () -> {
            // sleep longer than timeout to trigger TimeoutException
            ProcessUtils.runAndCapture(Arrays.asList("sh", "-c", "sleep 5; echo done"), 1);
        });
    }
}
