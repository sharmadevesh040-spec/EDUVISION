# AR EduVision — docs

Design and reference material for the AR EduVision platform.
Lives outside the repo at `C:\Users\vipul\VS CODE PROGRAM\areduvision\docs\`.

| File | Contents | Status |
|---|---|---|
| `architecture.md` | Layer diagram, package layout, request flow | TASK 3 |
| `api.md` | Full REST endpoint reference (auth, classes, contents, parts, questions, assignments) | TASK 3 |
| `domain-model.md` | 11 entities + 5 enums, relationships, ER notes | TASK 2 |
| `ar.md` | WebAR / `<model-viewer>` setup, CDN pins, online GLB model catalogue | TASK 5 |
| `runbook.md` | Build/run/verify commands, port rules, troubleshooting | TASK 1 (below) |

---

## Runbook (quick reference, current as of TASK 1)

### Ports
| Port | Owner |
|---|---|
| 8080 | `team\oclimit\local-switch-proxy.py` — team internet proxy. **NEVER kill.** |
| **8081** | **AR EduVision backend** (official) |
| 5173 | Vite frontend dev server (from TASK 4) |

### Backend
```cmd
cd areduvision\backend
build.cmd              :: mvn clean package -> target\eduvision-backend-1.0.0.jar
run.cmd background     :: detached, logs to target\backend.log
run.cmd                :: foreground
```

Verify:
```powershell
Invoke-WebRequest http://localhost:8081/api/health
# StatusCode : 200
# Content    : {"status":"ok"}
```

Stop:
```powershell
Stop-Process -Id (Get-Content areduvision\backend\target\backend.pid) -Force
```

### Toolchain
* JDK 25.0.2 at `C:\Program Files\Java\jdk-25.0.2`
* Maven 3.9.16 **project-local** at `areduvision\.tools\apache-maven-3.9.16\bin\mvn.cmd`
  (no system install, no admin, PATH untouched).
  Restore with `areduvision\tools\setup-maven.cmd` if `.tools\` is wiped.
* Database: H2 **file** mode at `areduvision\backend\data\eduvision.mv.db`
  (url `jdbc:h2:file:./data/eduvision`, user `sa`, blank password, `ddl-auto: update`).

### Troubleshooting
| Symptom | Cause / fix |
|---|---|
| `Port 8080 was already in use` | Expected — the team proxy owns 8080. `run.cmd` falls back to 8081. |
| `curl`/health check fails right after `run.cmd background` | Startup takes ~20-30 s on first run. Retry after 30 s, or read `target\backend.log`. |
| `.tools\...\mvn.cmd` not found | Run `areduvision\tools\setup-maven.cmd`. |
| `ERROR: Input redirection is not supported` | Never use `timeout /t` in a batch with redirected stdin. Use `ping -n N 127.0.0.1 >nul`. |