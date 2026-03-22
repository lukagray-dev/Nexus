# Nexus E-mail Service

Fallback/manual email API for Nexus authentication flows.

## Current Email Strategy

Nexus currently uses two paths:

| Path | Status | Purpose |
| --- | --- | --- |
| Supabase built-in Auth emails | Primary | Default signup confirmation and password reset emails |
| This Node.js service (`server.js`) | Fallback/Manual | OTP sending, OTP verification, and backup password-reset email flow |

Use Supabase first. Use this service when you need custom/manual control or a backup path.

## What This Folder Contains

- `server.js`: Express API for OTP and password-reset email handling.
- `supabase-email-template.html`: Supabase signup/confirmation template.
- `supabase-password-reset-template.html`: Supabase reset-password template.
- `env.example`: Environment variable template.
- `test-api.ps1` / `test-api.bat`: Quick API test scripts.

## API Endpoints

Base URL: `http://localhost:3000` (local)

- `GET /` - Health check.
- `GET /debug` - Runtime diagnostics (for debugging only).
- `POST /send-otp` - Generate/store OTP and send email.
- `POST /verify-otp` - Verify OTP and consume it.
- `POST /send-password-reset` - Generate reset link and send email.

### `POST /send-otp`

```json
{
  "email": "user@example.com"
}
```

### `POST /verify-otp`

```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

### `POST /send-password-reset`

```json
{
  "email": "user@example.com",
  "actionUrl": "https://your-app-url.com"
}
```

## Local Run

```bash
cd e-mail
npm install
npm run dev
```

Create `.env` from template:

```bash
# macOS/Linux
cp env.example .env

# Windows PowerShell
Copy-Item env.example .env
```

Health check:

```bash
curl http://localhost:3000/
```

## Environment Variables

This service reads values from `.env`.

Required for full production behavior:

- Firebase Admin credentials (`FIREBASE_*`)
- `FIREBASE_DATABASE_URL`
- `BREVO_API_KEY`
- `BREVO_SENDER_EMAIL`
- `BREVO_SENDER_NAME`

Behavior without full config:

- If Brevo is missing, mail sender falls back to mock mode.
- If Firebase Admin credentials are missing, database/auth behavior falls back to mock mode.

## Supabase Template Setup (Primary Path)

Use the included HTML templates in Supabase Dashboard:

1. Go to `Authentication -> Email Templates`.
2. For confirm signup, paste `supabase-email-template.html`.
3. For reset password, paste `supabase-password-reset-template.html`.

## Deployment Notes

- The service exports `module.exports = app`, so it can run in serverless environments.
- In non-production mode, `server.js` starts a local listener automatically.
- Keep `NODE_ENV=production` in hosted environments.

## Testing

- PowerShell: `./test-api.ps1`
- Batch: `test-api.bat`

## Security Notes

- Never commit `.env`.
- Never commit Firebase service-account secrets.
- Keep Brevo API keys and sender credentials private.
