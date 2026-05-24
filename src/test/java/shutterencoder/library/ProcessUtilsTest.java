package test.java.shutterencoder.library;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import shutterencoder.library.ProcessUtils;

public class ProcessUtilsTest {

    @Test
    public void testRunAndCaptureEcho() throws Exception {
        String out = ProcessUtils.runAndCapture(Arrays.asList("sh", "-c", "echo hello"), ProcessUtils.DEFAULT_SHORT);
        assertTrue(out.contains("hello"));
    }

    @Test(expected = java.util.concurrent.TimeoutException.class)
    public void testTimeout() throws Exception {
        // sleep longer than timeout to trigger TimeoutException
        ProcessUtils.runAndCapture(Arrays.asList("sh", "-c", "sleep 5; echo done"), 1);
    }
}
