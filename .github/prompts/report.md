## Report: External Process & Stream Handling Analysis

### DELIVERABLE 1: Files & Line Ranges with Process/Stream Interactions

**Core Library Files:**

| File | Line Ranges | Process Type |
|------|-------------|--------------|
| [FFMPEG.java](src/shutterencoder/library/FFMPEG.java) | 103, 105, 284, 296, 343, 393, 414, 419, 437, 735, 752, 759, 776, 923, 1002-1017, 2073 | ProcessBuilder → Process streams |
| [FFPROBE.java](src/shutterencoder/library/FFPROBE.java) | 44, 257, 260, 664, 722, 730-762, 897, 940-942 | ffprobe analysis processes |
| [YOUTUBEDL.java](src/shutterencoder/library/YOUTUBEDL.java) | 50, 100-150, 200+ | yt-dlp download process |
| [WHISPER.java](src/shutterencoder/library/WHISPER.java) | 79, 309, 443-444, 759, 814-850+ | whisper transcription process |
| [DEMUCS.java](src/shutterencoder/library/DEMUCS.java) | 27, 40, 98-210 | Audio separation process |
| [MEDIAINFO.java](src/shutterencoder/library/MEDIAINFO.java) | 60, 66, 73, 79, 216 | Media analysis process |
| [NCNN.java](src/shutterencoder/library/NCNN.java) | 75, 82, 89, 95, 171 | Neural network process |

**UI/Handler Files:**

| File | Line Ranges | Process Type |
|------|-------------|--------------|
| [VideoPlayer.java](src/shutterencoder/ui/videoplayer/VideoPlayer.java) | 115-118, 776-805, 1262-1323, 1747, 1835, 4011, 5222, 5805, 6183-6208 | ffmpeg preview players |
| [Utils.java](src/shutterencoder/utils/Utils.java) | 2558-2595, 2640-2676, 2663 | Registry/Process management |
| [UnlockExternalApps.java](src/shutterencoder/utils/UnlockExternalApps.java) | 20-150 | Registry query processes |
| [Picture.java](src/shutterencoder/functions/Picture.java) | 323-336 | Image generation process |

---

### DELIVERABLE 2: Code Excerpts - Process Creation & Stream Handling

**FFMPEG.java [Lines 283-296]** - Main encoding process:
```java
processFFMPEG = new ProcessBuilder('"' + PathToFFMPEG + '"' + " -strict " + 
    Settings.comboStrict.getSelectedItem() + " -hide_banner -threads " + 
    Settings.txtThreads.getText() + " " + cmd.replace("PathToFFMPEG", '"' + PathToFFMPEG + '"'));								
process = processFFMPEG.start();	

// Streams accessed but NOT closed:
BufferedReader input = new BufferedReader(new InputStreamReader(process.getErrorStream()));		
InputStream video = process.getInputStream();				
BufferedInputStream videoInputStream = new BufferedInputStream(video);	
OutputStream stdin = process.getOutputStream();
writer = new BufferedWriter(new OutputStreamWriter(stdin));
```
⚠️ **Issue**: Streams assigned to static variables, not closed after process ends.

**FFMPEG.java [Lines 413-437]** - Silent mode (minimal cleanup):
```java
ProcessBuilder processFFMPEG = new ProcessBuilder('"' + PathToFFMPEG + '"' + 
    " -strict " + Settings.comboStrict.getSelectedItem() + " -hide_banner " + cmd);								
process = processFFMPEG.start();	

BufferedReader input = new BufferedReader(new InputStreamReader(process.getErrorStream()));

while ((line = input.readLine()) != null) {			
    checkForErrors(line);
}					
process.waitFor();  // ⚠️ No timeout, no stream close
```

**FFMPEG.java [Lines 1002-1017]** - Cleanup in finally block (better pattern):
```java
try {
    video.close();
} catch (IOException e) {}		
try {
    videoInputStream.close();
} catch (IOException e) {}

if (audio != null) {
    try {
        audio.close();
    } catch (IOException e) {}
    try {
        audioInputStream.close();
    } catch (IOException e) {}
    line.close();	
}
```
✓ **Better**: Has close() calls but should use try-with-resources.

**VideoPlayer.java [Lines 776-805]** - Piped process creation:
```java
ProcessBuilder pbv = new ProcessBuilder("cmd.exe" , "/c",  '"' + PathToFFMPEG + '"' + 
    " -strict " + Settings.comboStrict.getSelectedItem() + " -hide_banner -threads " + 
    Settings.txtThreads.getText() + " " + cmd +  " | " + '"' + PathToFFMPEG + '"' + 
    " -v quiet -i pipe:0" + fps + " -c:v bmp -pix_fmt rgb24 -an -f image2pipe -");
process = pbv.start();

InputStream video = playerVideo.getInputStream();  // ⚠️ Not closed in error path

if (FFPROBE.hasAudio) {
    ProcessBuilder pba = new ProcessBuilder("cmd.exe" , "/c", ...);	
    processAudio = pba.start();  // ⚠️ Second process, can leak if first fails
}
```
⚠️ **Issues**: Multiple processes without error handling; stream not closed in exception path.

**Utils.java [Lines 2640-2676]** - Process destruction (cleanup):
```java
FFMPEG.process.destroy();  // ⚠️ destroy() instead of destroyForcibly()
FFPROBE.process.destroy();
BMXTRANSWRAP.process.destroy();
DCRAW.process.destroy();
DVDAUTHOR.process.destroy();
TSMUXER.process.destroy();
YOUTUBEDL.process.destroy();
PYTHON.process.destroy();
WHISPER.process.destroy();
```
⚠️ **Issue**: Uses `destroy()` (SIGTERM on Unix, TerminateProcess on Windows), not `destroyForcibly()` (SIGKILL).

**WHISPER.java [Line 443-444]** - Good pattern (try-with-resources):
```java
try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
    // Process output here
}  // ✓ Automatically closes
```
✓ **Best**: Uses try-with-resources for auto-close.

**DEMUCS.java [Lines 152-154]** - Missing explicit close:
```java
process = processBuilder.start();
BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

// ... read loop ...
process.waitFor();
// ⚠️ reader never explicitly closed
```

**UnlockExternalApps.java [Lines 20-38]** - Good try pattern:
```java
Process process = new ProcessBuilder("reg", "query", key, "/v", "ProgId").start();
try (BufferedReader reader = new BufferedReader(
    new InputStreamReader(process.getInputStream()))) {
    // ...
}
int exitCode = process.waitFor();  // ✓ Waits for completion
```

---

### DELIVERABLE 3: Identified Leak Sites

| File | Lines | Leak Type | Severity |
|------|-------|-----------|----------|
| [FFMPEG.java](src/shutterencoder/library/FFMPEG.java#L308-L370) | 308-370 | Static BufferedReader/InputStreams persists across calls | **CRITICAL** |
| [FFMPEG.java](src/shutterencoder/library/FFMPEG.java#L413-437) | 413-437 | runSilently() - reader never closed after waitFor() | **HIGH** |
| [VideoPlayer.java](src/shutterencoder/ui/videoplayer/VideoPlayer.java#L776-805) | 776-805 | Multiple piped processes, no exception cleanup | **HIGH** |
| [VideoPlayer.java](src/shutterencoder/ui/videoplayer/VideoPlayer.java#L1315-1335) | 1315-1335 | Player window close - destroy() called but streams may not drain | **MEDIUM** |
| [FFPROBE.java](src/shutterencoder/library/FFPROBE.java#L722-762) | 722-762 | Multiple threads spawned without join(); readers not closed | **HIGH** |
| [WHISPER.java](src/shutterencoder/library/WHISPER.java#L759-850) | 759-850 | reader created but no close() in finally or try-with | **MEDIUM** |
| [DEMUCS.java](src/shutterencoder/library/DEMUCS.java#L152-210) | 152-210 | reader created; no explicit close on error path | **MEDIUM** |
| [YOUTUBEDL.java](src/shutterencoder/library/YOUTUBEDL.java#L100-150) | 100-150 | Multiple readers: isr, br, ffmpegOutput - not closed | **MEDIUM** |
| [Utils.java](src/shutterencoder/utils/Utils.java#L2640-2676) | 2640-2676 | destroy() without destroyForcibly(); streams not drained | **MEDIUM** |
| [Picture.java](src/shutterencoder/functions/Picture.java#L323-336) | 323-336 | outputStream.close() called but stdin may have data | **LOW** |

---

### DELIVERABLE 4: Suggested Fixes

#### Fix 1: FFMPEG.java - Use try-with-resources for stream handling
```java
// BEFORE (Lines 413-437)
BufferedReader input = new BufferedReader(new InputStreamReader(process.getErrorStream()));
while ((line = input.readLine()) != null) {			
    checkForErrors(line);
}					
process.waitFor();

// AFTER
try (BufferedReader input = new BufferedReader(
        new InputStreamReader(process.getErrorStream()))) {
    String line;
    while ((line = input.readLine()) != null) {			
        checkForErrors(line);
    }
} finally {
    int exitCode = process.waitFor(30, TimeUnit.SECONDS);  // Add timeout
    if (!exitCode) {
        process.destroyForcibly();  // Use destroyForcibly
    }
}
```

#### Fix 2: FFMPEG.java - Handle static writer safely
```java
// BEFORE
public static BufferedWriter writer;
writer = new BufferedWriter(new OutputStreamWriter(stdin));

// AFTER
private static volatile BufferedWriter writer;
private static final Object writerLock = new Object();

private static void closeWriter() {
    synchronized (writerLock) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Log
            } finally {
                writer = null;
            }
        }
    }
}
```

#### Fix 3: VideoPlayer.java - Handle multiple processes safely
```java
// BEFORE
ProcessBuilder pbv = new ProcessBuilder(...);
process = pbv.start();

if (FFPROBE.hasAudio) {
    ProcessBuilder pba = new ProcessBuilder(...);	
    processAudio = pba.start();  // Leaks if throws
}

// AFTER
try {
    ProcessBuilder pbv = new ProcessBuilder(...);
    process = pbv.start();

    if (FFPROBE.hasAudio) {
        try {
            ProcessBuilder pba = new ProcessBuilder(...);	
            processAudio = pba.start();
        } catch (IOException e) {
            process.destroyForcibly();
            throw e;
        }
    }
} catch (IOException e) {
    logger.error("Failed to start process", e);
    if (process != null) process.destroyForcibly();
    if (processAudio != null) processAudio.destroyForcibly();
}
```

#### Fix 4: FFPROBE.java - Join threads before process cleanup
```java
// BEFORE
BufferedReader br = new BufferedReader(isr);
// Thread spawned to read but never joined
process.waitFor();

// AFTER
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<Void> readerTask = executor.submit(() -> {
    try (BufferedReader br = new BufferedReader(isr)) {
        String line;
        while ((line = br.readLine()) != null) {
            processLine(line);
        }
    }
    return null;
});

try {
    process.waitFor(60, TimeUnit.SECONDS);
    readerTask.get(5, TimeUnit.SECONDS);  // Wait for thread
} finally {
    executor.shutdownNow();
    process.destroyForcibly();
}
```

#### Fix 5: Utils.java - Upgrade destroy() calls
```java
// BEFORE
FFMPEG.process.destroy();

// AFTER
if (FFMPEG.process != null && FFMPEG.process.isAlive()) {
    FFMPEG.process.destroyForcibly();
    try {
        FFMPEG.process.waitFor(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

#### Fix 6: Generic pattern for all process spawning
```java
public static String runProcess(List<String> cmd, long timeoutSeconds) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);  // Merge stderr into stdout
    
    Process process = pb.start();
    StringBuilder output = new StringBuilder();
    
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
    }
    
    boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    if (!completed) {
        process.destroyForcibly();
        throw new InterruptedException("Process timeout after " + timeoutSeconds + "s");
    }
    
    if (process.exitValue() != 0) {
        throw new IOException("Process failed: " + output.toString());
    }
    
    return output.toString();
}
```

---

### DELIVERABLE 5: Other Files Requiring Similar Review

**Audio/Encoding Functions:**
- [AudioEncoders.java](src/shutterencoder/functions/AudioEncoders.java) - Spawns threads calling FFMPEG
- [VideoEncoders.java](src/shutterencoder/functions/VideoEncoders.java#L1012, L1234) - Creates writer/reader for XML processing
- [Extract.java](src/shutterencoder/functions/Extract.java#L292, L354) - Thread spawning for extraction

**Streaming/External Tools:**
- [PYTHON.java](src/shutterencoder/library/PYTHON.java) - Python process spawning (referenced in Utils destroy at line 2673)
- [DCRAW.java](src/shutterencoder/library/DCRAW.java) - RAW image processing
- [PDF.java](src/shutterencoder/library/PDF.java) - PDF handling
- [TSMUXER.java](src/shutterencoder/library/TSMUXER.java) - TS muxing
- [BMXTRANSWRAP.java](src/shutterencoder/library/BMXTRANSWRAP.java) - Broadcast media wrapping
- [EXIFTOOL.java](src/shutterencoder/library/EXIFTOOL.java) - Metadata extraction
- [LTCDUMP.java](src/shutterencoder/library/LTCDUMP.java) - LTC timecode dumping
- [ANONYMIZER.java](src/shutterencoder/library/ANONYMIZER.java) - Face anonymization

**Console/UI Handlers:**
- [Shutter.java](src/shutterencoder/ui/main/Shutter.java#L813) - Console reader creation

---

### Summary Table: Priority Fixes

| Priority | Component | Action | Impact |
|----------|-----------|--------|--------|
| **🔴 CRITICAL** | FFMPEG.java static fields | Refactor to use try-with-resources, close in finally | FD exhaustion in long-running sessions |
| **🔴 CRITICAL** | VideoPlayer.java multi-process | Add exception handling for process chains | Zombie processes on video preview errors |
| **🟠 HIGH** | FFPROBE.java stream threads | Join threads before process exit, use ExecutorService | Incomplete data reads, hanging threads |
| **🟠 HIGH** | Utils.java destroy() calls | Replace with destroyForcibly() + timeout | Graceful but firm process termination |
| **🟡 MEDIUM** | WHISPER/DEMUCS readers | Add finally blocks or try-with-resources | Possible handle leaks on error |
| **🟡 MEDIUM** | YOUTUBEDL reader cleanup | Explicitly close all 3 readers | Download process handle buildup |
| **🟢 LOW** | Picture.java outputStream | Ensure flush before close | Image generation may lose data |

All stream-related code should follow: **try-with-resources → process.waitFor(timeout) → destroyForcibly() on timeout → join() any reader threads**