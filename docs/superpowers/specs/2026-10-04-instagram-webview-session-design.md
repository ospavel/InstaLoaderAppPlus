# Instagram WebView session authentication

**Date:** 2026-10-04
**Status:** Design approved in chat
**Scope:** Replace direct Instagram username/password login with an interactive Instagram WebView flow and reuse its verified session in Instaloader.

## Context and goal

The app currently sends the username and password to Instaloader's direct web login endpoint. Instagram can reject that request with `feedback_required` before it returns the regular two-factor challenge. The app already has a login screen, a Python bridge, an Instaloader session layer, and a configurable request User-Agent.

The new flow lets the user complete login, authenticator 2FA, and any Instagram checkpoint on Instagram's own page. The app then imports and verifies the WebView cookies, stores the resulting session encrypted, and reuses it for later downloads. The password and TOTP code are not sent to Python.

## User flow

1. The Login screen offers **Sign in with Instagram** and opens an embedded Android WebView at `https://www.instagram.com/accounts/login/`.
2. The WebView provides Back, Reload, and Close controls. It permits HTTPS top-level navigation on `instagram.com` and its subdomains, plus `facebook.com` and its subdomains for Meta-owned login/checkpoint redirects. Other top-level destinations open outside the WebView; they do not receive Instagram cookies from this flow.
3. The user completes the entire website flow, including any authenticator code or checkpoint.
4. The user taps **Use this account**. Android reads the cookies for `https://www.instagram.com` with `CookieManager` and captures the WebView's actual User-Agent. The app does not infer success from a URL alone.
5. Python creates an Instaloader instance using that User-Agent, imports the cookies, and calls `test_login()`. On success it returns the verified username and the updated cookie dictionary. If no valid session is available, the screen stays open and shows a readable error; cookies are not persisted.
6. On success, the app stores username, cookie JSON, and the captured User-Agent in `EncryptedSessionStore`. The Login screen shows the signed-in account.
7. Downloads and profile-count requests load the encrypted session data into Instaloader in memory. No plaintext Instaloader session file is created.
8. Logout clears the encrypted session, the dedicated WebView cookie store, and any legacy Instaloader session file.

## Components and data flow

- **Compose login UI:** Replace the username, password, and app-managed TOTP fields with the WebView entry point, WebView navigation controls, the explicit session-import action, and login status/errors.
- **Session repository and encrypted store:** Add encrypted cookie JSON and session User-Agent alongside the encrypted username. Keep the existing session directory only for one-time legacy migration and cleanup.
- **Python bridge and Instaloader service:** Add an import-and-validate operation. Pass cookie JSON and session User-Agent to authenticated Python operations. Use Instaloader's cookie/session APIs and preserve current session application for downloads.
- **User-Agent precedence:** Use the saved WebView User-Agent for authenticated requests when the Settings field is blank. A nonblank manually configured User-Agent remains an explicit override. Anonymous requests continue using the existing default.
- **Existing-session migration:** On first use after upgrade, if the username and legacy `session-<username>` pickle exist but encrypted cookie data does not, load its cookie dictionary and copy it into encrypted preferences without a network request; delete the pickle only after the encrypted record is saved. Normal session validation then checks it online. If the legacy file cannot be decoded, clear only the stale signed-in indicator, preserve the file until a new WebView login succeeds or the user logs out, and make the WebView login available.

## Security and privacy

- The app never asks for, stores, or passes the password or TOTP code through its native form or Python bridge.
- Cookies are account credentials. They must not appear in logs, error details, analytics, or clipboard content.
- Persist cookies and the session User-Agent only in Android Keystore-backed encrypted preferences. Exclude the encrypted session preference file from cloud backup and device transfer because its encryption key is device-bound.
- The WebView uses HTTPS, JavaScript and DOM storage required by Instagram, Safe Browsing, and no JavaScript-to-native interface. File/content access stays disabled.
- Clear WebView cookies after a successful import and on logout. The WebView is dedicated to this flow, so this does not clear cookies from the user's external browser or Instagram app.

## Error handling and limits

- Missing `sessionid`, an incomplete login, a failed `test_login()`, network failures, and Python errors produce distinct user-facing messages. Diagnostics must never include cookie values.
- Instagram may reject embedded WebView login or a reused session. This flow does not bypass Instagram checkpoints or guarantee reduced blocking. If Instagram refuses the session, the app asks the user to resolve the issue on Instagram's page and try again.
- No random User-Agent rotation or invented browser identity is used. The captured WebView string is for session consistency, not a promise of avoiding rate limits or account checks.

## Verification

- Add focused tests for cookie-header parsing, JSON session transfer, and errors that must not leak cookie values.
- Build the signed `release` APK using the project's Docker build script and verify that the package is not debuggable.
- The release APK can be verified in the build environment. Completing a real Instagram login and checkpoint on EMUI 14.2 still requires the user's phone; Instagram may change the page or block embedded WebViews.

## Alternatives considered

- **Android Custom Tab or external browser:** Provides the user's browser experience and shared browser login state, but the app cannot read that browser's cookie jar to import the session automatically.
- **Continue direct password login and expose checkpoint URLs:** Preserves the current flow but does not help when Instagram returns `feedback_required` without a usable checkpoint URL, and it keeps collecting the password in the app.
- **Import cookies from a desktop browser profile:** Instaloader's CLI supports this, but it is not a good Android user flow and requires a separate cookie-export mechanism.
