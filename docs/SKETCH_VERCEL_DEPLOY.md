# Live sketch API (`/api/sketch`) — deploy notes

Android’s sketch client is ready. Debug builds default to the local mock proxy
(`http://10.0.2.2:8787` via `scripts/run-sketch-proxy.ps1`). Release builds call
`https://homedes-webapp.vercel.app`.

As of 2026-08-26, live `https://homedes-webapp.vercel.app/api/sketch` still
returns **404** until the webapp’s serverless functions are deployed with env
vars. That work is **outside this APK** (homedes-webapp + Vercel dashboard).

## Who does what

| Role | Action |
|------|--------|
| Android | Keep using local mock for E2E; optional `sketch.base.url` in `local.properties` |
| Web / ops | Deploy `homedes-webapp` to Vercel with convert env vars (see below) |

Canonical deploy guide (webapp repo):

`C:\webapp_android\homedes-webapp\DEPLOY.md`  
https://github.com/diveydoescode/homedes-webapp (file `DEPLOY.md`)

## Vercel checklist (summary)

1. Vercel project root = `homedes-webapp/` (`package.json` + `vercel.json`).
2. Framework: Vite → `npm run build` → `dist/`.
3. Environment variables (Production; **not** `VITE_*`):

   | Name | Example |
   |------|---------|
   | `CONVERT_HOST` | `https://sketch2cad-4142a3.azurewebsites.net` |
   | `CONVERT_API_KEY` | *(converter secret — Sensitive)* |

4. Deploy. Confirm routes exist:
   - `POST /api/sketch`
   - `GET /api/sketch/:id`
   - `GET /api/sketch/:id/file`
   - `POST /api/sketch/:id/cancel`
5. Smoke: upload JPEG &lt; 4 MiB from web or Android release pointing at Vercel.

## Android verify after web deploy

```powershell
# Release / override debug to Vercel:
# local.properties → sketch.base.url=https://homedes-webapp.vercel.app
cd C:\webapp_android\homedes-android
.\scripts\dev-up.ps1 -SkipTests
# App → New → From a sketch → pick photo → job should poll (not 404)
```

Until then, debug E2E:

```powershell
.\scripts\run-sketch-proxy.ps1   # mock + proxy on :8787
.\scripts\dev-up.ps1 -SkipTests  # emulator uses 10.0.2.2:8787
```

## Deferred

- Catalog 3D mesh pack (non-SH3D) — user will supply assets later.
