## Plan: Fix FFmpeg process/pipe leaks

TL;DR: Fix leaking OS handles and unclosed process pipes by refactoring process lifecycle management. Primary work is in `src/shutterencoder/library/FFMPEG.java` (remove static stream fields, use try-with-resources), add a shared Process utility for consistent spawn/cleanup, and update callers (FFPROBE, VideoPlayer, YOUTUBEDL, WHISPER, DEMUCS, Utils).

**Steps**
1. Refactor `FFMPEG.java` to stop storing streams in static fields and ensure all streams are closed with try-with-resources or explicit close in finally. *Depends on step 2.*
2. Implement a shared helper `ProcessUtils` (new file under `src/shutterencoder/library/`) that: starts processes, merges/stores stderr/stdout safely, reads streams with try-with-resources or ExecutorService, waits with a timeout, and calls `destroyForcibly()` on timeout. Replace ad-hoc patterns with calls to this helper.
3. Update other libraries to use `ProcessUtils`: `FFPROBE.java`, `YOUTUBEDL.java`, `WHISPER.java`, `DEMUCS.java`, and others identified in discovery. These edits can be done in parallel per-file.
4. Replace unconditional `process.destroy()` calls in `src/shutterencoder/utils/Utils.java` with a safe termination sequence: check `isAlive()`, `process.waitFor(timeout)`, then `destroyForcibly()` if necessary; and ensure any reader threads are joined/terminated.
5. Add explicit join/shutdown for any threads or ExecutorServices that read from process streams (e.g., FFPROBE reader threads). Ensure reader tasks finish before allowing the process object to be discarded.
6. Add logging around process start/stop and stream-close for easier diagnosis (info on start, warn on non-zero exit or forced destroy).
7. Verification: add a small local test/harness to repeatedly run a short ffmpeg job (or a safe echo command) in a loop and watch open file descriptor counts; run the UI preview/processing flows and confirm FFMPEG handles don't accumulate.

**Relevant files**
- `src/shutterencoder/library/FFMPEG.java` — primary leak sites; static stream/writer usage.
- `src/shutterencoder/library/FFMPEG.java` — runSilently/read loop without try-with-resources.
- `src/shutterencoder/library/FFMPEG.java` — partial cleanup present, needs consistency.
- `src/shutterencoder/utils/Utils.java` — global destroy() calls.
- `src/shutterencoder/ui/videoplayer/VideoPlayer.java` — piped processes and multiple starts.
- `src/shutterencoder/library/FFPROBE.java` — reader threads need join.
- `src/shutterencoder/library/WHISPER.java` — reader handling to improve.
- (Other candidates) `YOUTUBEDL.java`, `DEMUCS.java`, `MEDIAINFO.java`, `PYTHON.java` — review and update similarly.

**Verification**
1. Build the project and run a repeatable short encode via the UI or a small Java harness to start/stop FFMPEG many times.
2. Monitor process/file-descriptor counts with these commands while running the harness:
```bash
# find the Java PID (example)
pgrep -f shutter | head -n1
# replace <PID> with the Java process id
watch -n 1 "lsof -p <PID> | wc -l"
# or observe processes
watch -n 1 "ps -eo pid,cmd | grep -E 'ffmpeg|ffprobe' | wc -l"
```
3. Confirm fd count remains stable after many runs and no lingering ffmpeg processes remain (use `ps aux | grep ffmpeg`).
4. Run UI preview actions and long batch encodes to validate in-app usage.

**Decisions & Assumptions**
- Prefer adding a single `ProcessUtils` helper to reduce duplication and standardize timeouts/logging.
- Use `destroyForcibly()` as fallback after waiting, and ensure reader threads are joined before disposing process references.
- Small API changes to FFMPEG may be required (make methods non-static or protect static resources with proper lifecycle management).

**Next actions / Questions**
- Do you want me to prepare the code changes (implementation + tests) or just produce patches for review?
- Any preferred default timeout for external tools (suggest 30s for quick ops, 120s for longer transcodes)?
