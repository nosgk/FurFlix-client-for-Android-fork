# FurFlix

<p align="center">
  <a href="https://furrychx.github.io/FurFlix-client-for-Android/">🌐 Official Website</a>
</p>

<p align="center">
  <img src="screenshots/icon.png" width="140" style="border-radius: 22%;" alt="FurFlix">
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/AGP-9.4.1-3DDC84?logo=gradle&logoColor=white" alt="AGP">
</p>

<p align="center"><a href="README.md">English</a> · <a href="README_ZH.md">简体中文</a> · <a href="README_RU.md">Русский</a> · <a href="README_UA.md">Українська</a></p>

<p align="center">
  <sub><b>Keywords:</b> furaffinity android app · furaffinity client · furaffinity apk · furaffinity mobile · furry android client · furry art viewer android · anthro art viewer</sub>
</p>

Unofficial FurAffinity client for Android — fast, modern, built with Jetpack Compose.

> Not affiliated with FurAffinity or IMVU Inc.

---

## Screenshots

<p align="center">

<table align="center">
<tr>
  <td align="center" width="200"><b>Browse</b><br><img src="screenshots/browse.png" width="180"></td>
  <td align="center" width="200"><b>User Profile</b><br><img src="screenshots/userprofile.png" width="180"></td>
  <td align="center" width="200"><b>Tag Autocomplete</b><br><img src="screenshots/tagsearch.png" width="180"></td>
</tr>
</table>

</p>

---

## Features

### 📱 Feed
Infinite scroll through submissions, grid or list view. Log in for a personal feed from watched artists.

### 🔍 Search
Full-text and tag-based search with autocomplete. Filter by rating, type, category, species, gender, sort, and date range. Search state mirrors to a shareable URL.

### 🖼️ Post viewer
Full post card with image, artist, stats, tags, description, comments. Swipe between posts, pinch-to-zoom and pan. Inline document, PDF and animated GIF viewing.

### 👤 Profiles & watchlist
Artist profiles with gallery, scraps, favorites and folder tree. One tap to favorite or watch.

### ⬇️ Downloads
Save images with metadata to a dedicated section. Choice of display file or original file.

### 🔒 Content filters
Mute words in titles, toggle NSFW on/off. Cloudflare challenge is handled in-app via a real WebView.

### 🎨 Customization
Multiple color themes and app icon styles, 4 UI languages (EN / RU / UA / ZH), glassmorphism UI with blur effects. Optional on-device translation of scraped content.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.4.20 (AGP built-in Kotlin, KGP pinned via `buildscript`) |
| UI | Jetpack Compose, Material 3, BOM `2026.09.00` |
| DI / architecture | No framework; repository + ViewModel, singleton accessors |
| Network | OkHttp 5.4.0 + Jsoup 1.22.2 (HTML scraping) |
| Images | Coil 3.6.3 (compose / gif / network-okhttp) |
| Persistence | DataStore Preferences, Room 2.8.5 |
| Async | Kotlin Coroutines 1.10.2, Flow |
| Min / target SDK | 26 / 36 (compileSdk 37) |
| Modules | Single `:app` |

---

## Build & verification

**This project is not built locally.** All verification runs on GitHub Actions (`.github/workflows/build.yml`):

- `./gradlew :app:lintDebug` — lint, including the i18n translation audit
- `./gradlew :app:testDebugUnitTest` — JVM unit tests
- `./gradlew :app:assembleRelease -Pabi=arm64-v8a` — signed release APK
- debug APK is available via `workflow_dispatch` with `build_type: debug`

Push to `main` triggers the workflow. Release artifacts are uploaded per run.

> Local Gradle invocation is intentionally unsupported: the signing keystore and
> the pinned toolchain exist only in CI. See [ADR-0003](docs/ADRs/0003-no-local-builds.md).

---

## Contributing

Before opening a PR, read:

- `AGENTS.md` — repository conventions (i18n, commit style, no local builds)
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — how to add screens, components, strings
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module layout and data flow
- [`ISSUES.md`](ISSUES.md) — the live, prioritised problem list

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
`feat:`, `fix:`, `refactor:`, `docs:`, `build:`, `test:`, `chore:`.

### i18n rules in short

1. New user-visible strings go into `app/src/main/res/values/strings.xml` **and** the mirror
   position in `values-zh-rCN/strings.xml`.
2. Hardcoded user-visible text is a bug. In Composables use `stringResource(R.string.x)`;
   outside composition use `context.getString(...)`.
3. Missing keys falling back to English is by design; the zh file may lag temporarily.
4. Scraped site content (tags, join dates) is **not** localised via string resources —
   it is handled by the in-app translation feature.
5. Never add `locale` to `MainActivity`'s `android:configChanges` — it silently breaks
   in-app language switching.

---

## Known issues

See [`ISSUES.md`](ISSUES.md) for the structured, prioritised list (architecture,
complexity, duplication, tests, security, performance, i18n, CI).

---

## Support

FurFlix is built by a solo developer. If you find it useful:

<p>
<img src="https://img.shields.io/badge/Monobank-000000?style=for-the-badge&logo=monobank&logoColor=white" alt="Monobank">
<img src="https://img.shields.io/badge/USDT_(TRC20)-26A17B?style=for-the-badge&logo=tether&logoColor=white" alt="USDT">
<img src="https://img.shields.io/badge/Bitcoin-F7931A?style=for-the-badge&logo=bitcoin&logoColor=white" alt="Bitcoin">
</p>

<details>
<summary><b>Monobank</b> — Visa / Mastercard</summary>
<br>

| Currency | Card |
|---|---|
| UAH | `4874 1000 3088 2800` |
| USD | `4874 1000 8642 3608` |
| EUR | `4441 1144 9001 3347` |

Google Pay · [Monobank Jar](https://send.monobank.ua/jar/31dqVo9pHr)
</details>

<details>
<summary><b>USDT (TRC20)</b></summary>
<br>

```
TVX7biFztNzd2P85y8iDGTN6uLyuduGEj7
```
</details>

<details>
<summary><b>Bitcoin (BTC)</b></summary>
<br>

```
12mRZTgc1dPr8iqPmzuwszKK7Ee9uQafwX
```
</details>

---

## Contact

<a href="https://t.me/PavelFurrych"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram"></a>
<a href="https://x.com/Furrych"><img src="https://img.shields.io/badge/Twitter%2FX-000000?style=for-the-badge&logo=x&logoColor=white" alt="Twitter/X"></a>
<img src="https://img.shields.io/badge/Discord-furrych-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord">

---

*Third-party client. Use at your own discretion and in accordance with FurAffinity's terms of service.*
