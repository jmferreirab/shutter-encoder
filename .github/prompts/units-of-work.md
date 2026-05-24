**Units of Work**

1. **Create ProcessUtils helper:**  
   - Files: new `src/shutterencoder/library/ProcessUtils.java`  
   - Task: Implement a utility that starts processes, reads merged stdout/stderr, provides methods to (a) run-and-capture with timeout, (b) start long-lived process with stream readers via ExecutorService, and (c) safely terminate (wait + destroyForcibly).  
   - Acceptance: Unit tests for run-and-capture and timeout behavior; API documented.

2. **Add logging + constants:**  
   - Files: `src/shutterencoder/library/ProcessUtils.java`, update logging imports in callers.  
   - Task: Add consistent logging (info on start, warn on forced kill), and centralize default timeouts (e.g., `DEFAULT_SHORT=30s`, `DEFAULT_LONG=120s`).  
   - Acceptance: Logs produced during ProcessUtils unit tests.

3. **Refactor FFMPEG.java static streams:**  
   - Files: FFMPEG.java  
   - Task: Remove static `writer`, `process`-related static streams; make process instances local where possible or encapsulate lifecycle in ProcessUtils. Replace ad-hoc stream code with ProcessUtils calls. Ensure any writer access is synchronized and closable via a new `closeWriter()` method.  
   - Acceptance: No static unclosed stream fields remain; compile passes.

4. **Refactor FFMPEG.java read loops to try-with-resources:**  
   - Files: FFMPEG.java  
   - Task: Replace manual BufferedReader loops with try-with-resources and `process.waitFor(timeout)` + forced destroy fallback. Add proper exception handling and resource cleanup.  
   - Acceptance: Unit tests or run verifying process exit and no leaked threads when invoking small ffmpeg-like command.

5. **Create/Update FFPROBE integration to use ProcessUtils:**  
   - Files: FFPROBE.java  
   - Task: Replace reader threads with ExecutorService-backed reader tasks via ProcessUtils; ensure futures are joined before process disposal.  
   - Acceptance: FFprobe runs finish and reader tasks are joined in tests.

6. **Fix Video preview (`VideoPlayer.java`) process chain:**  
   - Files: VideoPlayer.java  
   - Task: Start piped ffmpeg processes with safe startup sequence: start first process, then second; on failure, destroy started processes via ProcessUtils; close streams in all paths.  
   - Acceptance: Preview start/stop repeatedly without leaving ffmpeg processes.

7. **Replace global destroy() usage in `Utils.java`:**  
   - Files: Utils.java  
   - Task: Replace `process.destroy()` calls with safe termination sequence: `isAlive()`, `waitFor(timeout)`, then `destroyForcibly()` and `waitFor()`; null out process references.  
   - Acceptance: Utils cleanup terminates child processes reliably in verification runs.

8. **Audit & update other libraries:**  
   - Files: `YOUTUBEDL.java`, `WHISPER.java`, `DEMUCS.java`, `MEDIAINFO.java`, `PYTHON.java`, etc.  
   - Task: For each, switch to ProcessUtils or add try-with-resources + join readers; close all readers/writers in finally. Prioritize ones flagged in report.  
   - Acceptance: Each updated file compiles and unit test harness confirms no lingering processes after run.

9. **Add harness and verification tests:**  
   - Files: new `tools/process-leak-test/` (small Java harness + README)  
   - Task: Create a harness that runs a quick external command (safe echo or small ffmpeg job) in a loop, tracks `lsof`/fd counts and ensures fd/process counts remain stable. Provide commands to run monitoring (see plan).  
   - Acceptance: Harness demonstrates stable fd counts after 100+ iterations.

10. **Integration testing & PR prep:**  
    - Files: project changes across modified files + tests  
    - Task: Run full build, run harness and UI preview/manual smoke tests, fix regressions, create a PR with changelog and testing notes. Include suggested default timeouts and rationale.  
    - Acceptance: PR ready with tests and verification instructions; CI (if available) passes.

**Execution notes**
- Work in small commits per unit (one unit = one branch/PR ideally).  
- Default timeout suggestions: `30s` for short operations, `120s` for longer analyses; make timeouts configurable.  
- Verification commands to run while testing (copyable):
```bash
pgrep -f shutter | head -n1
watch -n 1 "lsof -p <PID> | wc -l"
watch -n 1 "ps -eo pid,cmd | grep -E 'ffmpeg|ffprobe' | wc -l"
```
