# CORTEX CONTROL API

A small HTTPS-ready API for account authentication, owner-only device management, explicit device pairing, and command delivery.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/vino989/Contex-1.0)

## Deploy (Render)

1. Open the [Render deployment page](https://render.com/deploy?repo=https://github.com/vino989/Contex-1.0) and sign in.
2. Connect GitHub and select `vino989/Contex-1.0` if asked.
3. Render reads `server/render.yaml` and proposes the API web service and PostgreSQL database. Review the plan and any charges before approving.
4. Click **Apply** / **Deploy Blueprint** and wait for the deploy to finish.
5. Open the service's public `onrender.com` URL. The root page should show **API service is online**; `/health` should return JSON with `"ok": true`.
6. In Render's service environment, copy the generated `OWNER_SETUP_KEY` and keep it private. Do not put it in the APK or GitHub.
7. Initialize the creator account exactly once:

```bash
curl -X POST https://YOUR-SERVICE.onrender.com/api/auth/register-owner \
  -H 'Content-Type: application/json' \
  -H 'X-Owner-Setup-Key: YOUR_OWNER_SETUP_KEY' \
  -d '{"password":"USE_A_NEW_UNIQUE_PASSWORD_12_CHARS_MIN"}'
```

The response contains a bearer token. Treat it as a secret. The username is always `12111`; the owner account cannot be created through ordinary registration. Keep the owner password private.

## API overview

- `GET /` — public status page.
- `GET /health` — machine-readable health check.
- `POST /api/auth/register` — ordinary user registration (10+ character password).
- `POST /api/auth/login` — login; returns JWT.
- `POST /api/auth/register-owner` — one-time owner bootstrap protected by server-only setup key.
- `GET /api/me` — current account and role.
- `POST /api/pairing/request` — friend phone creates a six-digit code (10-minute expiry).
- `POST /api/pairing/claim` — owner account `12111` claims that code.
- `GET /api/pairing/pending` — friend phone checks for a claim.
- `POST /api/pairing/:id/approve` — friend explicitly approves remote access.
- `POST /api/pairing/:id/revoke` — friend revokes access.
- `GET /api/devices` — owner lists actively paired devices.
- `POST /api/devices/:id/commands` — owner queues an allow-listed command.
- `GET /api/device/commands` — paired phone polls its command queue.
- `POST /api/device/commands/:id/result` — paired phone reports completion/rejection.

## Integration status and safety

This is a backend foundation. The Android app still needs API integration: secure token/device-secret storage, approval UI, polling, and Android-side allow-listed command execution. Deploying the API does not automatically complete the Android app.

The command API intentionally excludes arbitrary shell access, hidden camera/microphone access, silent SMS sending, and invisible screen control. The friend can revoke pairing at any time. Any screen sharing or camera feature must use Android's visible system consent flow.

Keep database credentials, JWT secret, and owner setup key out of source control and APK files. Free Render web services can sleep after inactivity, and free Postgres databases expire after 30 days; review [Render free-plan limits](https://render.com/docs/free) before choosing a free database for persistent data.
