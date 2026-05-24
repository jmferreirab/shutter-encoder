# Incremental AI-Agent Tasks

Summary: This is a media transcoding tool. You are to fix leaking OS handles and unclosed process pipes that leave hanging processes such as ffmpeg.exe. Primary work is in src/shutterencoder/library/FFMPEG.java (remove static stream fields, use try-with-resources), add a shared Process utility for consistent spawn/cleanup, and update callers (FFPROBE, VideoPlayer, YOUTUBEDL, WHISPER, DEMUCS, Utils). 

Do not introduce timeouts, your goal is just to ensure streams/readers/writers are closed when the process actually finishes (or when the app requests a graceful stop). Do not overengineer it and keep changes minimal.

## Task 1 — Discovery Audit

**Goal:** Identify every external process lifecycle implementation.

### Scope

Search for:

* `ProcessBuilder`
* `Runtime.exec`
* `getInputStream`
* `getErrorStream`
* `getOutputStream`
* `waitFor`
* `destroy`
* reader threads
* static `Process` / stream fields

### Deliverables

* List of affected files
* Notes about:

  * leaked streams
  * missing closes
  * reader threads not joined
  * duplicated process handling logic
  * piped/chained processes

### Likely files

* `FFMPEG.java`
* `FFPROBE.java`
* `WHISPER.java`
* `YOUTUBEDL.java`
* `DEMUCS.java`
* `MEDIAINFO.java`
* `PYTHON.java`
* `VideoPlayer.java`
* `Utils.java`

---

# Task 2 — Create `ProcessUtils`

**Goal:** Centralize process lifecycle management and stream handling.

### File

`src/shutterencoder/library/ProcessUtils.java`

### Responsibilities

* Start processes
* Consume stdout/stderr safely
* Encapsulate reader-thread logic
* Close streams safely
* Standardize graceful shutdown
* Reduce duplicated code

### Requirements

* Use try-with-resources where applicable
* Ensure readers/writers/streams are always closed
* Ensure reader threads complete before cleanup
* Avoid static mutable stream ownership

### Deliverables

* New utility class
* Reusable stream-consumer abstraction

---

# Task 3 — Refactor `FFMPEG.java`

**Goal:** Remove leak-prone process and stream handling.

### File

`src/shutterencoder/library/FFMPEG.java`

### Required changes

* Remove static stream/reader/writer fields
* Replace ad-hoc process handling with `ProcessUtils`
* Refactor `runSilently` and similar methods
* Ensure:

  * stdout/stderr are fully consumed
  * streams are closed after process completion
  * readers terminate cleanly
  * graceful stop paths clean up correctly

### Important

Do not interrupt valid long-running transcoding jobs. Cleanup should happen only:

* after natural process completion
* or after explicit app-requested stop

---

# Task 4 — Refactor Remaining Process Users

**Goal:** Migrate remaining classes to `ProcessUtils`.

### Files

* `FFPROBE.java`
* `WHISPER.java`
* `YOUTUBEDL.java`
* `DEMUCS.java`
* `MEDIAINFO.java`
* `PYTHON.java`
* `VideoPlayer.java`
* `Utils.java`

### Focus areas

* duplicated stream handling
* unclosed readers/writers
* lingering reader threads
* inconsistent destroy/cleanup behavior
* repeated process restarts in preview/player flows

### Parallelization

Each file can be handled independently after `ProcessUtils` is complete.

---

# Task 5 — Add Lifecycle Logging

**Goal:** Make leaks and stuck processes diagnosable.

### Add logs for

* process start
* process completion
* graceful stop requests
* forced cleanup paths
* stream close failures
* unexpected process termination

### Deliverables

Consistent lightweight logging across all process-based classes.

---

# Task 6 — Verification Harness

**Goal:** Verify handles/processes do not accumulate.

### Create

Small repeat-execution harness that:

* launches short ffmpeg/ffprobe commands repeatedly
* waits for completion
* verifies processes exit cleanly

### Validate

* no accumulating file descriptors/handles
* no lingering ffmpeg child processes
* no growing thread count
* no leaked reader threads

### Manual validation

Test:

* preview start/stop repeatedly
* encode cancellation
* app shutdown during processing
* multiple sequential jobs
* long-running transcodes finishing naturally
