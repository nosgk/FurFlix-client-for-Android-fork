# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Simplified Chinese (zh-CN) localization**: full `values-zh-rCN/strings.xml`
  dictionary (172 keys, 1:1 with the default file), a Chinese entry plus a
  "system default" option in the in-app language switcher, `locales_config` for
  Android 13+ per-app language settings, and `localeFilters` resource shrinking
  (drops ~80 unused AAR locale tables). All previously hardcoded UI strings —
  including upstream Russian literals that leaked to non-Russian users — are
  extracted into resources; comment dates now render in the active locale.
  Design blueprint: internal planning document (intentionally not committed).
- Baseline profile for startup hot paths (`app/src/main/baseline-prof.txt`) with
  `androidx.profileinstaller` — improves cold start and first-frame scrolling.
- Consistent release signing: PKCS12 keystore stored as GitHub Actions secrets,
  applied to release builds via `signingConfigs`; CI prints the certificate
  fingerprint on every build (`apksigner verify --print-certs`) so signature
  consistency is auditable.
- Default CI build is now a **signed release APK for arm64-v8a**; debug builds
  (all ABIs) are available on demand via `workflow_dispatch` (`build_type: debug`).
- Privacy audit: the app contains **no telemetry** — no analytics, crash
  reporting or tracking SDKs; all network traffic goes to furaffinity.net only.

### Changed
- **SEO / website**: meta description, Open Graph and JSON-LD structured data,
  `robots.txt`, `sitemap.xml` (without XHTML alternates), IndexNow key, and
  Google Search Console verification; repository links added to the page footer.
- **Tech stack upgrade** (required by the Haze 2.x cascade):
  - Kotlin 2.1.10 → 2.4.20 (compose & serialization plugins aligned), KSP → 2.3.12
  - Android Gradle Plugin 8.13.2 → 9.4.1, Gradle 8.13 → 9.6.0 (required by
    Compose 1.12.x AAR metadata); KGP kept via the AGP 9 built-in-Kotlin opt-out
  - `compileSdk` 36 → 37 (required by Haze 2.x; `targetSdk` intentionally kept at 36)
  - Compose BOM 2024.11.00 → 2026.09.00 (Compose 1.12.1, Material 3 1.4.0)
  - Lifecycle 2.8.7 → 2.11.0, Navigation Compose 2.8.4 → 2.10.2
  - Room 2.6.1 → 2.8.5, DataStore unchanged
  - kotlinx-coroutines 1.9.0 → 1.10.2, kotlinx-serialization 1.7.3 → 1.9.0
  - AppCompat 1.6.1 → 1.7.0
  - `kotlinOptions {}` migrated to the `kotlin { compilerOptions {} }` DSL

### Changed — Coil 2 → Coil 3 (3.6.3)
- Coordinates moved from `io.coil-kt` to `io.coil-kt.coil3`; all imports migrated
  (`coil.*` → `coil3.*`).
- Coil 3 no longer bundles a network stack: the OkHttp fetcher
  (`coil-network-okhttp`) is now registered explicitly and wired to the app's
  custom OkHttp client, preserving the FurAffinity `User-Agent` / `Referer` /
  `Cookie` headers via `OkHttpNetworkFetcherFactory(callFactory = ...)`.
- GIF support moved to the `coil3-gif` artifact; decoders (`GifDecoder`,
  `ImageDecoderDecoder`) keep working under `coil3.decode`.
- Loader-level `crossfade` was removed in Coil 3; fades remain on per-request
  `ImageRequest.Builder.crossfade(...)` call sites only (plain string-model
  `AsyncImage`s no longer fade in).

### Changed — Haze 1.7.2 → 2.0.1
- New required artifact pair: `dev.chrisbanes.haze:haze` + `haze-blur`.
- Blur API reworked: `Modifier.hazeEffect(state, style)` →
  `Modifier.hazeBlur(input = HazeInput.Sources(state), style = ...)`;
  `HazeStyle`/`HazeTint` replaced by the immutable `HazeBlurStyle { }` builder
  with `HazeColorEffect.tint(...)`.
- `HazeState` and `hazeSource` are unchanged (core package).

## [1.0] - Initial release
- Unofficial FurAffinity client for Android: feed (Browse / Latest / Search)
  with infinite scroll, post viewer with zoomable images and downloads,
  user profiles, watchlist, tag autocomplete with 200K+ offline tags,
  NSFW toggle, login, themes and app-icon variants.

[Unreleased]: https://github.com/nosgk/FurFlix-client-for-Android-fork/compare/v1.0...HEAD
[1.0]: https://github.com/nosgk/FurFlix-client-for-Android-fork/releases/tag/v1.0
