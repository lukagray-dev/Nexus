# Contributing to Nexus

Thanks for your interest in contributing to Nexus.

## Before You Start

- Use this project only for lawful parental control and child safety use cases.
- Never submit secrets, private keys, service account files, or personal data.
- Read `SECURITY.md` before reporting sensitive issues.

## Development Setup

### General

- Git
- Node.js 18+ (for desktop and email service)
- Android Studio + Android SDK (for Android apps)
- JDK 11

### Project Areas

- `Nexus Child/android` for child agent work
- `Nexus Parent/mobile/android` for parent Android dashboard
- `Nexus Parent/desktop` for parent desktop dashboard
- `e-mail` for OTP/password-reset backend
- `landing` for static website updates

## Workflow

1. Fork the repository.
2. Create a branch from `main`:
   - `feat/<short-name>` for features
   - `fix/<short-name>` for bug fixes
   - `docs/<short-name>` for documentation changes
3. Keep changes scoped to one concern per pull request.
4. Add or update documentation when behavior changes.
5. Open a pull request with clear context.

## Commit Message Guidance

Use concise, descriptive commit messages.

Examples:

- `feat(child): add reconnect handling for signaling`
- `fix(email): prevent duplicate otp sends`
- `docs(readme): clarify module setup steps`

## Pull Request Checklist

- Build/tests pass for the touched module(s)
- No credentials or generated secrets included
- README/docs updated if needed
- Screenshots attached for UI changes
- Clear reproduction steps for bug fixes

## Reporting Bugs

When opening an issue, include:

- Affected module/path
- Steps to reproduce
- Expected result
- Actual result
- Logs or screenshots (sanitized)
- Environment details (OS, SDK, app version)

## Security Issues

Do not open public issues for security vulnerabilities.

Report privately to: `soumom764@gmail.com`

## Code Style Expectations

- Prefer readable, modular code over large monolithic blocks.
- Keep platform conventions:
  - Kotlin: idiomatic naming and null-safety.
  - JavaScript: small focused functions and explicit error handling.
  - HTML/CSS: consistent structure and naming.
- Remove dead code and debug leftovers before submitting.

## License

By contributing, you agree that your contributions will be licensed under the MIT License in this repository.
