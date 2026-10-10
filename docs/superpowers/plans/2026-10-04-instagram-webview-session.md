# Instagram WebView Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user authenticate on Instagram's own page, verify and store the resulting session securely, and reuse it for Instaloader downloads.

**Architecture:** An Android WebView handles Instagram's interactive login and checkpoint pages. Kotlin transfers its cookie header and actual User-Agent to the Python bridge, which verifies the session with Instaloader and returns the normalized cookie jar. Android stores the verified cookies and User-Agent in Keystore-backed encrypted preferences; Python receives the session in memory for each authenticated operation.

**Tech Stack:** Kotlin, Jetpack Compose `AndroidView`, `android.webkit.WebView` and `CookieManager`, AndroidX `EncryptedSharedPreferences`, Chaquopy, Python 3.14, the project's existing Instaloader 4.15.3 dependency, Gradle, Docker release builder.

**Spec:** `docs/superpowers/specs/2026-10-04-instagram-webview-session-design.md`

## Global Constraints

- Never ask for, store, or pass the password or TOTP code through a native form or Python bridge.
- Persist cookies and the session User-Agent only in Android Keystore-backed encrypted preferences; exclude the encrypted session preference file from cloud backup and device transfer.
- Permit HTTPS top-level WebView navigation on `instagram.com` and its subdomains, plus `facebook.com` and its subdomains for Meta-owned login/checkpoint redirects.
- Use the saved WebView User-Agent for authenticated requests when the Settings field is blank. A nonblank manually configured User-Agent remains an explicit override.
- Cookies are account credentials. They must not appear in logs, error details, analytics, or clipboard content.
- Use the captured User-Agent for session consistency; do not rotate or invent a browser identity and do not promise it prevents restrictions.
- Build the signed `release` APK using the project's Docker build script and verify that the package is not debuggable.

## Review Focus

- Cookie values containing `=` or malformed headers must parse without truncation; missing `sessionid` must fail without echoing cookie values. Pin this in Task 1's parser tests.
- A logged-out or incomplete WebView must not persist a session when `test_login()` returns no username or raises. Pin this in Task 2's verification tests.
- Importing a different valid Instagram account must replace the old username, cookies, and User-Agent as one session record. Pin this in Task 3's store tests.
- Legacy session migration must preserve the old pickle until encrypted preferences contain its cookie data; unreadable legacy data must leave login available. Pin cookie conversion in Task 2's Python tests and migration ordering in Task 3's repository tests.
- Instagram URL lookalikes and insecure `http:` redirects must not remain inside the WebView; pin the policy in Task 5's host-policy tests.

---

## File Structure

**Create:**

- `app/src/main/java/com/alphacorp/instaloader/data/model/InstagramSession.kt` — username, cookie JSON, and session User-Agent value object.
- `app/src/main/java/com/alphacorp/instaloader/data/repository/SessionBackend.kt` — backend contract for session verification, encrypted persistence, and legacy-file migration, allowing repository tests with a fake backend.
- `app/src/main/java/com/alphacorp/instaloader/data/repository/AndroidSessionBackend.kt` — production adapter composed from `PythonBridge`, `EncryptedSessionStore`, and the legacy session directory.
- `app/src/main/java/com/alphacorp/instaloader/ui/login/InstagramLoginWebView.kt` — WebView composition, controls, cookie capture, and lifecycle cleanup.
- `app/src/main/java/com/alphacorp/instaloader/util/InstagramLoginUrlPolicy.kt` — HTTPS/host allowlist used by WebView navigation.
- `app/src/test/java/com/alphacorp/instaloader/util/InstagramLoginUrlPolicyTest.kt` — host policy unit tests.
- `app/src/test/python/test_instagram_session.py` — cookie parsing, validation, migration, and session application tests.
- `app/src/test/python/test_instaloader_options.py` — request User-Agent precedence tests.
- `app/src/androidTest/java/com/alphacorp/instaloader/data/local/EncryptedSessionStoreTest.kt` — encrypted session round-trip, replacement, and clearing test.
- `app/src/test/java/com/alphacorp/instaloader/data/repository/SessionRepositoryTest.kt` — verification, legacy migration ordering, and failure-path tests using a fake backend.

**Modify:**

- `app/src/main/python/instaloader_service/session.py` — browser-cookie validation, JSON session conversion, in-memory session application, and legacy file conversion.
- `app/src/main/python/bridge.py` — expose the new session operations without exposing cookie values in errors.
- `app/src/main/python/instaloader_service/options.py` and `downloader.py` — choose the session-aware User-Agent and apply encrypted session data for downloads and profile counts.
- `app/src/main/java/com/alphacorp/instaloader/data/python/PythonBridge.kt` — typed wrappers for the Python session operations.
- `app/src/main/java/com/alphacorp/instaloader/data/local/EncryptedSessionStore.kt` — encrypted session read/write/clear API.
- `app/src/main/java/com/alphacorp/instaloader/data/repository/SessionRepository.kt` — verify, persist, restore, migrate, and clear sessions.
- `app/src/main/java/com/alphacorp/instaloader/data/repository/DownloadRepository.kt` and `di/AppModule.kt` — share session snapshots with downloads.
- `app/src/main/java/com/alphacorp/instaloader/ui/login/LoginScreen.kt` and `LoginViewModel.kt` — replace password/2FA forms with the WebView flow and account status.
- `app/src/main/res/values/strings.xml` — login actions, status, and privacy text.
- `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` — exclude encrypted session preferences from backup/transfer.
- `app/build.gradle` — increment the app version for the installable release.
- `README.md` — document WebView login, session storage, and current login limitations.

## Interfaces

- Python `parse_cookie_header(cookie_header: str) -> dict[str, str]` returns cookie pairs, splitting each pair only at its first `=` and requiring a nonempty `sessionid`.
- Python `verify_browser_session(cookie_header: str, user_agent: str) -> str` returns JSON with `username` and normalized `cookies` only after `Instaloader.test_login()` succeeds.
- Python `legacy_session_json(username: str, session_file: str) -> str` loads an existing pickle and returns the same JSON envelope without a network request. Legacy sessions have no captured WebView User-Agent, so Kotlin stores an empty session User-Agent and uses the existing manual override or default.
- Python `apply_session(loader, username: str, cookies_json: str) -> None` calls `loader.load_session(username, json.loads(cookies_json))` when an encrypted session exists.
- Kotlin `InstagramSession` contains `username: String`, `cookiesJson: String`, and `userAgent: String`.
- Kotlin `SessionBackend` provides browser verification, legacy pickle conversion, encrypted read/write/clear, and legacy-file existence/deletion operations. `AndroidSessionBackend` delegates Python calls to `PythonBridge`, storage to `EncryptedSessionStore`, and legacy-file operations to the session directory. Tests use a fake backend.
- Kotlin `SessionRepository.importBrowserSession(cookieHeader: String, userAgent: String, sessionDir: String): InstagramSession` verifies first and persists only the verified payload.
- Kotlin `SessionRepository.currentSession(sessionDir: String): InstagramSession?` returns encrypted session state or migrates one legacy pickle before returning.
- Python download option keys are `session_username`, `session_cookies_json`, and `session_user_agent`; existing `user_agent` remains the manual override.

---

### Task 1: Parse Android cookie headers

**Files:** modify `app/src/main/python/instaloader_service/session.py`; create `app/src/test/python/test_instagram_session.py`.

**Interfaces:** Produces `parse_cookie_header(cookie_header: str) -> dict[str, str]` for Task 2.

- [x] **Step 1: Write failing parser tests.** Use `unittest`; include values with `=` and a header without `sessionid`.

```python
def test_cookie_parser_preserves_equals_and_requires_sessionid(self):
    self.assertEqual(
        parse_cookie_header("sessionid=abc==; csrftoken=xyz"),
        {"sessionid": "abc==", "csrftoken": "xyz"},
    )
    with self.assertRaises(LoginException):
        parse_cookie_header("csrftoken=not-a-session")
```

- [x] **Step 2: Run the targeted test and confirm the parser is missing.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_instagram_session.py' -v`
Expected: FAIL because `parse_cookie_header` is not implemented.

- [x] **Step 3: Implement the parser.** Split the header on `;`, split each pair only at its first `=`, trim the name and value, ignore empty pairs, and raise an error with no cookie contents if `sessionid` is absent.

- [x] **Step 4: Run the targeted test and commit.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_instagram_session.py' -v`
Expected: PASS for padding, repeated separators, empty input, and token values containing `=`.

---

### Task 2: Verify and convert Instaloader sessions

**Files:** modify `session.py` and `app/src/main/python/bridge.py`; extend `test_instagram_session.py`.

**Interfaces:** Produces `verify_browser_session(cookie_header: str, user_agent: str) -> str`, `legacy_session_json(username: str, session_file: str) -> str`, and `apply_session(loader, username: str, cookies_json: str) -> None`.

- [x] **Step 1: Write failing tests with fake Instaloader objects.** Cover valid cookies, a rejected `test_login()`, legacy conversion, and in-memory `load_session`.
- [x] **Step 2: Run the targeted tests and confirm the three operations are missing.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_instagram_session.py' -v`
Expected: FAIL at the missing browser-session operations.

- [x] **Step 3: Implement `verify_browser_session`.** Create Instaloader with the captured User-Agent, call `context.update_cookies(parse_cookie_header(cookie_header))`, call `test_login()`, and raise a cookie-value-free `LoginException` if it returns no username. In Instaloader 4.15.3, `test_login()` returns a username but does not set `context.username`; call `loader.load_session(username, loader.context.save_session())` before the login-required `loader.save_session()`. Return JSON with verified username and normalized cookies only after success.
- [x] **Step 4: Implement `legacy_session_json` and `apply_session`.** Load the old pickle with `load_session_from_file`, return `loader.save_session()` as JSON without a network call, and apply JSON with public `loader.load_session`. Leave deletion of the old file to Kotlin after encrypted persistence succeeds.
- [x] **Step 5: Expose the Python functions and run the complete Python suite.** The bridge wrapper accepts and returns strings only; it must not log cookie headers.

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_*.py' -v`
Expected: PASS, including preexisting filter-parser tests. Commit only the Task 2 files.

---

### Task 3: Store verified sessions encrypted and migrate old files

**Files:** create `InstagramSession.kt`, `SessionBackend.kt`, `AndroidSessionBackend.kt`, `app/src/androidTest/java/com/alphacorp/instaloader/data/local/EncryptedSessionStoreTest.kt`, and `app/src/test/java/com/alphacorp/instaloader/data/repository/SessionRepositoryTest.kt`; modify `EncryptedSessionStore.kt`, `SessionRepository.kt`, `PythonBridge.kt`, and `app/build.gradle`.

**Interfaces:** `InstagramSession(username: String, cookiesJson: String, userAgent: String)`; `SessionRepository.importBrowserSession(cookieHeader: String, userAgent: String, sessionDir: String): InstagramSession`; `SessionRepository.currentSession(sessionDir: String): InstagramSession?`.

- [x] **Step 1: Add failing storage and repository tests before implementation.** The instrumentation test saves Alice's session, replaces it with Bob's, checks the complete snapshot, clears it, and verifies the preference XML does not contain the literal cookie value. The JVM `SessionRepositoryTest` uses a fake backend to cover account replacement, failed verification, and migration save/delete ordering.
- [x] **Step 2: Configure Android instrumentation tests to use the release build and implement the session types/store.** Set `testBuildType 'release'` so Gradle only creates release instrumentation tasks, matching the user's request not to build a debug app. Use synchronous `commit()` when saving the three session values so legacy files are not deleted before encrypted persistence succeeds. `clearSession()` removes username/cookies/User-Agent but preserves the session-directory preference.
- [x] **Step 3: Add the production backend and repository import flow.** `AndroidSessionBackend` delegates Python calls to `PythonBridge`, encrypted reads/writes to `EncryptedSessionStore`, and legacy-file operations to the session directory. `importBrowserSession` verifies first, stores the returned username/cookie jar/User-Agent as one record, then removes a legacy pickle for that account.
- [x] **Step 4: Implement lazy legacy migration and failure handling.** If username exists but cookie JSON is absent, convert `session-<username>` without network access; delete the pickle only after encrypted `commit()` succeeds. If decoding fails, clear the stale signed-in record, keep the old file, and return `null` so WebView login is available.
- [x] **Step 5: Run repository unit tests and compile release encrypted-store instrumentation tests.** The release unit suite passed, and `assembleReleaseAndroidTest` produced the instrumentation APK. Runtime instrumentation remains pending because no ADB device or emulator was connected.

Run: `sh docker/build-apk.sh testReleaseUnitTest assembleReleaseAndroidTest`
Run (Docker, JDK 17): `sh docker/build-apk.sh testReleaseUnitTest assembleReleaseAndroidTest`
Expected: JVM repository tests pass; the instrumentation APK compiles and, on an attached device or emulator, encrypted replacement/clear and plaintext-exclusion checks pass. Commit only the Task 3 files.

---

### Task 4: Apply encrypted sessions and User-Agent to downloads

**Files:** modify `app/src/main/python/instaloader_service/options.py`, `downloader.py`, `app/src/main/java/com/alphacorp/instaloader/data/repository/DownloadRepository.kt`, `app/src/main/java/com/alphacorp/instaloader/data/python/PythonBridge.kt`, and `app/src/main/java/com/alphacorp/instaloader/di/AppModule.kt`; create `app/src/test/python/test_instaloader_options.py`.

**Interfaces:** Python download options are `session_username`, `session_cookies_json`, `session_user_agent`, and the existing manual `user_agent` override.

- [x] **Step 1: Write failing User-Agent precedence tests.** With a fake Instaloader constructor, assert manual User-Agent wins, captured session User-Agent is used when the manual value is blank, and `DEFAULT_USER_AGENT` is the final fallback.

```python
loader = build_instaloader({"user_agent": "", "session_user_agent": "Android WebView UA"})
self.assertEqual(loader.context.user_agent, "Android WebView UA")
```

- [x] **Step 2: Run the targeted options test and confirm session User-Agent is ignored.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_instaloader_options.py' -v`
Expected: FAIL on the session User-Agent assertion.

- [x] **Step 3: Implement Python precedence and session loading.** In `options.py`, select manual User-Agent, then session User-Agent, then the existing default. In `downloader.py`, pass cookie JSON into `apply_session` for all targets and profile counts; do not read or write a pickle in normal download paths.
- [x] **Step 4: Pass one shared session snapshot from Kotlin.** Inject the `SessionRepository` created by `AppModule` into `DownloadRepository`. Resolve `currentSession()` before `getPostCount` and `startDownload`; pass its username, cookie JSON, and User-Agent in the bridge options. Keep the existing manually configured User-Agent in the `user_agent` option.
- [ ] **Step 5: Run the complete Python and JVM test suites and commit.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_*.py' -v`
Run: `sh docker/build-apk.sh testReleaseUnitTest`
Expected: all Python and Android unit tests pass.

---

### Task 5: Add an HTTPS Instagram/Meta WebView navigation policy

**Files:** create `app/src/main/java/com/alphacorp/instaloader/util/InstagramLoginUrlPolicy.kt` and `app/src/test/java/com/alphacorp/instaloader/util/InstagramLoginUrlPolicyTest.kt`.

**Interfaces:** Produces `InstagramLoginUrlPolicy.classify(url: String): Destination` for Task 6, where `Destination` is `WEBVIEW`, `EXTERNAL_HTTPS`, or `BLOCK`.

- [x] **Step 1: Write failing JVM tests.** Use `java.net.URI` so tests do not require Android runtime classes.

```kotlin
assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://www.instagram.com/"))
assertEquals(Destination.WEBVIEW, InstagramLoginUrlPolicy.classify("https://accountscenter.facebook.com/"))
assertEquals(Destination.BLOCK, InstagramLoginUrlPolicy.classify("http://instagram.com/"))
assertEquals(Destination.EXTERNAL_HTTPS,
    InstagramLoginUrlPolicy.classify("https://instagram.com.evil.example/"))
```

- [x] **Step 2: Run the targeted JVM test and confirm it fails because the policy is absent.**

Run: `sh docker/build-apk.sh testReleaseUnitTest`
Expected: FAIL on the missing `InstagramLoginUrlPolicy` type.

- [x] **Step 3: Implement the host check.** Require the `https` scheme and exact `instagram.com` or `facebook.com` host names, or a dot-delimited subdomain of those domains. Return `WEBVIEW` for allowed hosts, `EXTERNAL_HTTPS` for other valid HTTPS destinations, and `BLOCK` for malformed URLs or non-HTTPS schemes. Reject lookalike suffixes and credential-bearing URLs.
- [ ] **Step 4: Run the JVM tests and commit.**

Run: `sh docker/build-apk.sh testReleaseUnitTest`
Expected: PASS for the approved hosts and rejection cases. Commit only the URL-policy source and test.

---

### Task 6: Build the WebView login screen and connect session import

**Files:** create `app/src/main/java/com/alphacorp/instaloader/ui/login/InstagramLoginWebView.kt`; modify `LoginScreen.kt`, `LoginViewModel.kt`, and `app/src/main/res/values/strings.xml`; remove direct login/2FA wrappers from `PythonBridge.kt` and `session.py` after all callers are removed.

**Interfaces:** `InstagramLoginWebView` emits `(cookieHeader: String, userAgent: String)` only after the user presses **Use this account**; `LoginViewModel` passes these values to `SessionRepository.importBrowserSession`.

- [x] **Step 1: Add WebView component with navigation and policy tests.** Use `AndroidView`; load `https://www.instagram.com/accounts/login/`; enable JavaScript, DOM storage, cookies, and Safe Browsing where supported; disable file/content access and mixed content; add no JavaScript interface; cancel SSL errors. Keep Back, Reload, Close, and **Use this account** controls visible.
- [x] **Step 2: Implement URL handling and cookie capture.** Use Task 5's `WEBVIEW` result for in-WebView navigation, `EXTERNAL_HTTPS` for `ACTION_VIEW`, and cancel `BLOCK`. On **Use this account**, call `CookieManager.getCookie("https://www.instagram.com")`, capture `settings.userAgentString`, and send both to the ViewModel. Keep the WebView open if the cookie is missing or validation fails.
- [x] **Step 3: Replace password and app-managed TOTP fields.** Update `LoginViewModel` to restore `currentSession`, import a browser session asynchronously, show the verified username, and map failures to safe user-facing messages. Update the screen and warning text so no password or TOTP is collected natively.
- [x] **Step 4: Implement WebView cleanup.** Destroy the WebView when closed. Clear its app-local cookies after verified import, when the login flow is canceled, and on logout. Logout clears encrypted session state and any legacy session files.
- [x] **Step 5: Run release unit tests and instrumentation compilation.** Release unit tests passed and the release instrumentation APK compiled; runtime instrumentation was not run because no Android target was attached.

Run: `sh docker/build-apk.sh testReleaseUnitTest assembleReleaseAndroidTest`
Expected: URL-policy and Android unit tests pass, and the instrumentation APK compiles. Commit only the login-flow files.

---

### Task 7: Exclude session secrets from backup and prepare the release

**Files:** modify `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`, `README.md`, and `app/build.gradle`.

**Interfaces:** Consumes the completed WebView session flow; produces a signed release APK that follows the approved spec.

- [x] **Step 1: Exclude session secrets from backup.** Exclude `sharedpref/instaloader_session.xml` and the legacy `filesDir/sessions/` pickle directory from legacy full-backup rules and from both Android 12+ cloud-backup and device-transfer rules.
- [x] **Step 2: Update user-facing privacy and build documentation.** Describe WebView login, encrypted local cookie storage, the User-Agent default/override behavior, cookies as account credentials, and the possibility that Instagram rejects embedded WebViews. Clarify that the WebView sends credentials directly to Instagram, while the app does not collect the password or TOTP in a native form or pass them to Python or a developer server.
- [x] **Step 3: Increment app version.** Change the current `versionCode 3` to `4` and `versionName "2.0.1"` to `"2.0.2"` so the signed APK updates the installed release.
- [x] **Step 4: Run all final tests and build the release.**

Run: `PYTHONPATH="app/build/python/pip/release/common:app/src/main/python" /opt/homebrew/bin/python3 -m unittest discover -s app/src/test/python -p 'test_*.py' -v`
Run: `sh docker/build-apk.sh testReleaseUnitTest assembleRelease`
Expected: all Python and JVM tests pass and `app/build/outputs/apk/release/app-release.apk` is signed, version `2.0.2`/code `4`, and not debuggable.

- [x] **Step 5: Verify APK metadata and commit the release-ready files.**

Run:

```sh
docker run --rm --platform linux/amd64 -v "$PWD:/workspace" -w /workspace \
  instaloaderapp-android-builder:local \
  /opt/android-sdk/build-tools/34.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release.apk
docker run --rm --platform linux/amd64 -v "$PWD:/workspace" -w /workspace \
  instaloaderapp-android-builder:local \
  /opt/android-sdk/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Expected: `aapt` reports application ID `com.alphacorp.instaloader`, version `2.0.2`, and no `application-debuggable` flag; `apksigner` reports a verified release signature. Commit only Task 7 files.

## Execution Order

Complete Tasks 1–7 in order. Tasks 2–4 depend on the cookie parser and session JSON contract from Tasks 1–3. Task 6 depends on the URL policy, repository, and download integration from Tasks 3–5. Task 7 depends on the completed login flow. The user will install the release APK and test Instagram WebView login on EMUI 14.2; report that live result separately from build verification.
