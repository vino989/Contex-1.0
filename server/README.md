# CORTEX CONTROL API

A small HTTPS-ready API for account authentication, owner-only device management, explicit device pairing, and command delivery.

## Deploy (Render)

1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint** and select this repository.
3. Review `server/render.yaml`. The blueprint creates a web service and PostgreSQL database. A paid database/web plan may be required; check current Render pricing before deploying.
4. Wait for deployment. Open `https://YOUR-SERVICE.onrender.com/health`; expect `{"ok":true,...}`.
5. In Render service environment, copy the generated `OWNER_SETUP_KEY` value and keep it private. Do not put it in the APK or GitHub.
6. Initialize the creator account exactly once:

```bash
curl -X POST https://YOUR-SERVICE.onrender.com/api/auth/register-owner \
  -H 'Content-Type: application/json' \
  -H 'X-Owner-Setup-Key: YOUR_OWNER_SETUP_KEY' \
  -d '{"password":"USE_A_NEW_UNIQUE_PASSWORD_12_CHARS_MIN"}'
```

The response contains a bearer token. Treat it as a secret. The username is always `12111`; the owner account cannot be created through ordinary registration. Remove or rotate the setup key after initialization.

## API overview

- `POST /api/auth/register` — ordinary user registration (10+ character password).
- `POST /api/auth/login` — login; returns JWT.
- `POST /api/auth/register-owner` — one-time owner bootstrap protected by server-only setup key.
- `GET /api/me` — current account and role.
- `POST /api/pairing/request` — authenticated friend phone creates a six-digit code (10-minute expiry).
- `POST /api/pairing/claim` — owner account `12111` claims that code.
- `GET /api/pairing/pending` — friend phone checks for a claim.
- `POST /api/pairing/:id/approve` — friend explicitly approves remote access.
- `POST /api/pairing/:id/revoke` — friend revokes access.
- `GET /api/devices` — owner lists actively paired devices.
- `POST /api/devices/:id/commands` — owner queues an allow-listed command.
- `GET /api/device/commands` — paired phone polls its command queue.
- `POST /api/device/commands/:id/result` — paired phone reports completion/rejection.

All endpoints except `/health`, registration and login require a JWT or device credentials as documented by the endpoint. Device credentials are issued only to the device creating a pairing request and must be stored securely on that device.

## Safety and current integration status

This server is a backend foundation, not a complete remote-control product. The Android app in this repository has **not yet been wired to these API endpoints**. The device-side command executor, secure token storage, UI approval screen, periodic polling, and Android permission flows must be implemented before remote actions work.

The command API is intentionally allow-listed. It does not provide arbitrary shell access, hidden camera/microphone access, silent SMS sending, or invisible screen control. Any screen sharing or camera feature must use Android's visible system consent flow and a persistent indicator. The friend can revoke pairing at any time.

Use HTTPS in production. Keep database credentials, JWT secret, and owner setup key out of source control and APK files. Add rate limiting, audit logging, backups, and a production security review before public release.
