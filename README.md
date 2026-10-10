<div align="center">

# InstaLoaderApp

### Bulk Instagram media downloader for Android.

Download profiles, posts, reels, and hashtags using [`instaloader`](https://github.com/instaloader/instaloader) — with live progress, full CLI options, and optional login.

![GitHub release](https://img.shields.io/github/v/release/AzeemIdrisi/InstaLoaderApp)
![GitHub Repo stars](https://img.shields.io/github/stars/AzeemIdrisi/InstaLoaderApp?style=social)
![GitHub all releases](https://img.shields.io/github/downloads/azeemidrisi/instaloaderapp/total)

</div>

## Overview

**InstaLoaderApp** uses Jetpack Compose, foreground downloads with notifications, and instaloader 4.15.3 via Chaquopy. Files save to **`Downloads/InstaLoaderApp/`** by default (changeable in Settings → Storage).

> [!TIP]
> Login is optional — only needed for private content, stories, highlights, or comments.

---

## Screenshots

<div align="center">

<img height="600px" src="screenshots/1.png" />
<img height="600px" src="screenshots/2.png" />
<img height="600px" src="screenshots/3.png" />

</div>

---

## Features

- Profiles, single post/reel URLs, and hashtags
- Live progress (done / left / failed) + notification
- Full Instaloader settings (media, metadata, filters, naming, resume)
- Optional Instagram WebView login with two-factor and checkpoint flows
- Public download folder + custom location presets

---

## Installation

[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">](https://github.com/AzeemIdrisi/InstaLoaderApp/releases/latest)

Install the APK. The current storage implementation asks for **All files access** (Android 11+) on the first download because it writes directly to public folders. This permission covers shared storage beyond the download folder; grant it only if you trust the app, and revoke it after use if you prefer. **Notifications** permission is requested on Android 13+.

---

## Usage

| Input           | Example                     | Output folder                             |
| --------------- | --------------------------- | ----------------------------------------- |
| Username        | `instagram`                 | `Downloads/InstaLoaderApp/<username>/`    |
| Post / reel URL | `https://instagram.com/p/…` | `Downloads/InstaLoaderApp/posts/`         |
| Hashtag         | `#travel`                   | `Downloads/InstaLoaderApp/hashtag_<tag>/` |

1. Enter a username, URL, or hashtag → **Start download**.
2. For posts/reels, copy the link from Instagram and paste into the app.

If a download fails, expand **Error details** in the progress card to see and copy the full exception. Review the details before sharing them, since request URLs may contain private information.

> [!WARNING]
> Large profiles can take a long time. Instagram may restrict accounts used for automated downloading — use a secondary account at your own risk if you log in.

---

## Instagram login and session safety

Open **Login** and sign in on Instagram's page inside the app. Enter your password and any authentication code only in that page. The WebView sends them directly to Instagram; the app does not collect them in native fields or pass them to Instaloader's Python code or a developer server.

After you tap **Use this account**, InstaLoaderApp checks the WebView cookies with Instaloader. Only a confirmed session is saved. The cookie data and the WebView's actual User-Agent are stored together in encrypted app preferences protected by Android Keystore, and excluded from Android backup and device transfer. Cookies act like account credentials: keep the phone protected and log out when you no longer need the session. Logging out removes the saved session, legacy session files, and WebView cookies. Closing an incomplete login flow also clears WebView cookies.

The WebView cookie store is used while you sign in. Downloads use the captured WebView User-Agent for that session when the **User-Agent** setting is blank; a manually configured value takes precedence. The app keeps this browser identity consistent and does not rotate or invent a User-Agent. This does not prevent Instagram from challenging or restricting the account. Instagram may reject an embedded WebView or require an additional checkpoint; a session which Instaloader cannot verify will not be imported.

Older Instaloader session files are converted to encrypted cookie data locally when the app first restores that session. The old file is deleted only after encrypted storage succeeds.

---

## Build from source

### Docker (macOS)

Docker Desktop is required. The builder image contains JDK 17, Python 3.14, Android SDK Platform 35, and Android SDK Build-Tools 34.0.0. On Apple Silicon, Docker runs the Linux AMD64 builder through its built-in emulation. No Android SDK or JDK installation on macOS is required.

Build the signed release APK:

```sh
sh docker/build-apk.sh
```

The first build downloads the builder image and Gradle dependencies. Later builds reuse the Docker image and the `instaloaderapp-gradle-cache` volume. The APK is written to `app/build/outputs/apk/release/app-release.apk`. A release build requires a local `keystore.properties` and signing key; both are excluded from Git. Back up both files, since the same key is needed to update an installed APK later.

The script accepts Gradle tasks too, for example:

```sh
sh docker/build-apk.sh testReleaseUnitTest assembleRelease
```

### Native macOS build

Install [Android Studio](https://developer.android.com/studio) and use its SDK Manager to install Android SDK Platform 35 and Android SDK Build-Tools 34.0.0. Install a [JDK 17 build for macOS ARM64](https://adoptium.net/temurin/releases/?version=17), then point `JAVA_HOME` at it. Chaquopy 17 also requires Python 3.14; the configured path in `app/build.gradle` is `/opt/homebrew/bin/python3`.

Set `sdk.dir` in `local.properties`, then run:

```sh
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleRelease
```

---

## Disclaimer

Use responsibly. Download only content you may access. The developer is not responsible for misuse. During login, credentials are sent by the Instagram WebView to Instagram. The app does not send them to a developer server.

Licensed under [GPL-3.0](LICENSE). Contributions welcome via pull request.

---

## Developer

<a href="https://github.com/azeemidrisi/">
 <img width="150px" src="https://github.com/AzeemIdrisi/PhoneSploit-Pro/assets/112647789/a5fa646c-93a2-460f-bcb7-528fedb147e9" />
</a>

**Azeem Idrisi** - [@AzeemIdrisi](https://github.com/azeemidrisi/)

### Contributors

Special thanks to all the contributors :

[@noobshubham](https://github.com/noobshubham/)

[@PuruSinghvi](https://github.com/PuruSinghvi)

<a href="https://paypal.me/AzeemIdrisi" target="_blank"><img src="https://github.com/AzeemIdrisi/AzeemIdrisi/blob/main/docs/paypal-button-blue.png" alt="PayPal" width="147"></a>
<a href="https://www.buymeacoffee.com/AzeemIdrisi" target="_blank"><img src="https://github.com/AzeemIdrisi/AzeemIdrisi/blob/main/docs/default-yellow.png" alt="Buy Me A Coffee" width="200"></a>

<hr>

Copyright © 2026 Azeem Idrisi ([github.com/AzeemIdrisi](https://github.com/AzeemIdrisi))
