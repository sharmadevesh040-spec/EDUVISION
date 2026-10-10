# AR EduVision

An **AR-based educational platform**. Students and teachers explore interactive 3D models in
AR and in a 3D viewer, annotate their parts, answer curated questions, and receive graded
assignments.

| Layer | Stack |
|---|---|
| **Backend** | Java 25 + Spring Boot 3.5.16 (Maven) |
| **Frontend** | React + Vite + TypeScript + TailwindCSS |
| **3D / AR** | `<model-viewer>` (WebAR, Scene Viewer, WebXR) + three.js + jsQR |
| **Database** | H2 (file mode) via Spring Data JPA / Hibernate |
| **3D assets** | Fetched live from the public internet (modelviewer.dev / jsdelivr CDN) |

---

## 1. Prerequisites

Already installed on this machine — nothing else to install:

| Tool | Version | Location |
|---|---|---|
| JDK | **25.0.2** | `C:\Program Files\Java\jdk-25.0.2` |
| Node.js | v24.13.0 | system |
| npm | 11.6.2 | system |
| Maven | **3.9.16 (project-local)** | `areduvision\.tools\apache-maven-3.9.16\bin\mvn.cmd` |

> **Maven is NOT installed system-wide and does not need to be.**
> The repo ships a self-contained Maven under `areduvision\.tools\`.
> No admin rights, no PATH edits. `build.cmd` / `run.cmd` call it directly with
> `JAVA_HOME=C:\Program Files\Java\jdk-25.0.2`.
>
> If `.tools\` is ever wiped, restore it in ~90 s with:
> ```
> areduvision\tools\setup-maven.cmd
> ```

---

## 2. Ports — read this before starting anything

`server.port` is **8080** in `application.yml`, **but port 8080 is permanently held on this
machine** by `team\oclimit\local-switch-proxy.py` — the HTTP CONNECT proxy on
`127.0.0.1:8080` that carries this box's internet (captive portal). **Never kill it.**

`run.cmd` detects the clash and automatically falls back to **port 8081**.

> ### ✅ EduVision API base URL = `http://localhost:8081`
>
> Health check: `http://localhost:8081/api/health` -> `{"status":"ok"}`

Force a different port any time with:

```cmd
set EDUVISION_PORT=8090
```

---

## 3. Run the backend

### Build (produces the fat jar)
```cmd
cd areduvision\backend
build.cmd
```
Tail of a good build:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  42.682 s
=== BUILD SUCCESS === jar: ...\backend\target\eduvision-backend-1.0.0.jar
```

Output jar: **`areduvision\backend\target\eduvision-backend-1.0.0.jar`**

### Run in the background (recommended)
```cmd
cd areduvision\backend
run.cmd background
```
Writes `target\backend.log`, `target\backend.err`, `target\backend.pid`, then curls
`/api/health` for you.

### Run in the foreground (live logs, Ctrl+C to stop)
```cmd
cd areduvision\backend
run.cmd
```

### Verify it is alive
```powershell
Invoke-WebRequest http://localhost:8081/api/health | Select-Object StatusCode, Content
```
```
StatusCode : 200
Content    : {"status":"ok"}
```

### Stop it
```powershell
Stop-Process -Id (Get-Content areduvision\backend\target\backend.pid) -Force
```

---

## 4. Run the frontend

The React app is scaffolded in **TASK 4**. Until then `frontend\` holds only a placeholder.

Once scaffolded it will run as:
```cmd
cd areduvision\frontend
npm install
npm run dev
```
-> Vite dev server on <http://localhost:5173>, proxying `/api` -> `http://localhost:8081`.

---

## 5. Online 3D model sources

The AR viewer streams real models straight from the internet — nothing is vendored.

| Model | Size | URL |
|---|---|---|
| DamagedHelmet | 3.8 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/DamagedHelmet/glTF-Binary/DamagedHelmet.glb |
| AntiqueCamera | 17.5 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/AntiqueCamera/glTF-Binary/AntiqueCamera.glb |
| BarramundiFish | 12.5 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/BarramundiFish/glTF-Binary/BarramundiFish.glb |
| Avocado | 8.1 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/Avocado/glTF-Binary/Avocado.glb |
| ToyCar | 5.4 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/ToyCar/glTF-Binary/ToyCar.glb |
| BoomBox | 10.6 MB | https://cdn.jsdelivr.net/gh/KhronosGroup/glTF-Sample-Assets@main/Models/BoomBox/glTF-Binary/BoomBox.glb |

All are HTTP 200 and CORS-friendly. CDN JS available on `cdn.jsdelivr.net`:
`@google/model-viewer@4.0.0`, `three@0.160.0`, `jsqr@1.4.0`.

---

## 6. Project layout

```
areduvision\
├── README.md                     <- you are here
├── .gitignore
├── .tools\                       <- project-local Apache Maven 3.9.16 (gitignored)
│   └── apache-maven-3.9.16\bin\mvn.cmd
├── tools\
│   └── setup-maven.cmd           <- one-time Maven fetch (no admin, no PATH changes)
├── backend\                      <- Java 25 / Spring Boot 3.5.16 REST API
│   ├── pom.xml
│   ├── build.cmd                 <- mvn clean package  -> target\eduvision-backend-1.0.0.jar
│   ├── run.cmd                   <- start backend (auto-picks 8080 / 8081)
│   ├── start-backend.ps1         <- detached launcher used by run.cmd background
│   ├── src\main\java\com\eduvision\
│   │   ├── Application.java      <- @SpringBootApplication main
│   │   └── HealthController.java <- GET /api/health -> {"status":"ok"}
│   ├── src\main\resources\
│   │   └── application.yml       <- port, H2 file DB, JPA, UTC
│   ├── target\                   <- build output (gitignored)
│   └── data\                     <- H2 database files at runtime (gitignored)
├── frontend\                     <- React + Vite + TS + Tailwind (scaffolded in TASK 4)
│   ├── README.md                 <- placeholder
│   └── src\
└── docs\                         <- architecture / API / AR notes
```

---

## 7. Roadmap

| Task | Scope |
|---|---|
| **1** | Scaffold + Java build/run toolchain, local Maven, `/api/health` — **done** |
| **2** | Domain model (11 entities, 5 enums), repositories, PBKDF2 `PasswordUtil`, `DataSeeder` |
| **3** | Full REST API (auth, classes, contents, parts, questions, assignments, submissions) |
| **4** | React + Vite + TS + Tailwind scaffold |
| **5** | 3D viewer + WebAR (`<model-viewer>`) |
| **6** | Frontend UI: classes, lessons, AR viewer, quiz |
| **7** | End-to-end integration + seed walkthrough |
| **8** | Polish, docs, final verification |

---

## 8. Deploy to Vercel

The repo ships a complete **serverless mirror** of the API so the app runs without the
Java backend:

| File | Purpose |
|---|---|
| `api/[...all].js` | Full API handler (auth, classes, contents, quiz grading, analytics, AI tutor, live classroom). `student1` responses hide answer keys; `teacher`/`developer` see them. |
| `api/seed.json` | Demo data regenerated 1:1 from the real Java backend (44 endpoints captured, all HTTP 200). |
| `vercel.json` | `framework: null` + rewrites so `/api/*` hits `api/[...all].js` FIRST, then static files, then the SPA fallback. |

Deployment checklist:

- Import the repo **at its root** (do NOT set Root Directory to `frontend/` — the
  `api/` folder must be visible to Vercel).
- General-purpose deployment, no override for the Build Command is needed
  (`frontend` on its own builds straight to `frontend/dist`, which Vercel serves).
- After deploy, Functions → `api/[...all].js` should be listed, and
  `GET https://<your-app>.vercel.app/api/health` must return `{"status":"ok"}`.

Demo logins (serverless mode):

- Student: `student1@eduvision.com` / `Student@123`
- Teacher: `teacher@eduvision.com` / `Teacher@123`
- Developer: `developer@eduvision.com` / `Developer@123`

Known serverless limits: live-session state is held in the function instance (30 min
TTL) — cold starts may reset it; this is fine for demos but a production build should
use a shared store. Quiz grading, auth and analytics are stateless and fully serverless-safe.