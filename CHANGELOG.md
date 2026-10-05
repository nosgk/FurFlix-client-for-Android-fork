# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Browsing experience batch** (browse-ux RFC): user-adjustable gallery grid
  columns (auto-by-width or fixed 2-5) applied to every submission grid;
  thumbnail quality preference (auto / data saver / high) with cards now
  negotiating their own CDN tier from the measured lane width — previously
  only the masonry prefetch negotiated, so warmed URLs never matched what
  cards actually requested; a stats overlay on grid/masonry cards
  (favorites / views / comments, pink heart when favorited) backfilled from
  a unit-tested LRU stats cache fed by detail-page parses — FA listings
  carry no such numbers, so locally unknown submissions stay clean; tag
  long-press menu on the detail page (search / append to the origin list's
  keyword group / save as quick preset / copy) with a quick-preset chip row
  on the search tab; a per-item storage management screen (image cache,
  translation cache, search history, viewed-submission stats, bundled
  read-only tag lexicon); a two-pane detail layout on wide screens
  (artwork left, independently scrolling info pane right; auto at >=600dp
  with a manual override in Appearance); and lazy-grid content types with
  stable footer keys for smoother scrolling. Residual hardcoded strings
  ("No favorites yet.", meta labels, share-chooser title) extracted into
  resources with zh-CN mirrors.
- **Author gallery strip** (two-pane detail): the wide-screen info pane
  gains an "Author Gallery" section that anchors the current submission in
  the author's works — highlighted tile with prior works to its left — and
  keeps paging as the strip scrolls; served by the author gallery listing
  (NSFW/muted-word filters already applied) with quality-negotiated
  thumbnails.
- **Viewing history** (Profile tab): a third "Viewing History" tab lists
  locally viewed submissions newest-first, built from the stats cache which
  now also carries display fields (title/author/thumbnail/rating); rows use
  the familiar list cards with a count + clear header; re-opening a cached
  submission bumps its recency. The storage screen entry is renamed
  accordingly.
- **Gallery tile stats**: author-gallery tiles show the favorites/views/
  comments overlay (pink heart when favorited) for locally known
  submissions, in a two-row compact layout sized for the tile.
- **Adjustable two-pane split**: the detail layout's artwork pane share is
  a slider in Appearance (50-75%, 5% steps, default 65%) with live preview
  and a percentage readout.
- **Explicit thumbnail tiers**: the vague auto/data-saver/high dial becomes
  direct selection of every tier FA's CDN serves (200/300/320/400/600px),
  labeled with pixel values; stored legacy values migrate to tiers
  300/600 and the negotiator falls back to auto for unknown values.
- **Online translation** (opt-in, Settings → General → Translation): on-demand
  and auto translation of post descriptions (HTML-preserving for capable
  providers), tags, comments and user bios. Five built-in providers —
  Microsoft Edge (default: keyless and reachable from mainland China), two
  Google free endpoints, Azure Translator, and OpenAI-compatible APIs with
  custom base URLs for relays. Bilingual / translation-only display modes per
  content category, page-level tag toggle, persistent LRU translation cache,
  concurrency + request-interval limiting, retry with backoff, in-flight
  request coalescing, and Chinese-skip detection for zh targets. Zero new
  runtime dependencies; introduces the repo's first JVM unit test source set
  (template renderer / JSONPath / text splitter / Chinese detection / profile
  codec) now gating CI via `testDebugUnitTest`.
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
- **Masonry gallery view**: third gallery layout (grid / masonry / list) on
  `LazyVerticalStaggeredGrid`, switchable from the top bar and from a
  segmented control in Appearance. Listing thumbnails now parse the
  `data-width`/`data-height` attributes FA already ships, so masonry cards
  pre-size to the real artwork ratio (zero jump on load; ratios clamped to
  keep extreme artwork from unbalancing the lanes) and get tier-negotiated
  CDN thumbnails (`DynamicThumbnail`, 600px ceiling) with a first-screen
  prefetch — grid/list keep their fixed ratios.
- **Threaded comments (楼中楼)**: FA's reply nesting (container width style)
  is parsed into an indentation level and rebuilt into a tree by a unit-tested
  pure function; comments render as a flattened list with stateless connector
  lines, sp-aware folding by container width, and an in-place focus mode
  (branch drill-down under the same thread root, back key unwinds, target row
  flashes). Failed comment rows degrade to hidden comments instead of
  disappearing, keeping counts consistent.
- **Search hardening**: `SearchFilters` is now `@Serializable` and persisted
  (last-used filters restored as panel prefill, NSFW gate re-applied on top);
  the POST form is built by a deterministic, unit-tested builder with an
  explicit `perpage=72` shared between the request and the full-page
  pagination heuristic (no more wasted request on the last page); empty
  results render the empty state instead of an error banner; search tag chips
  support `!tag` exclusion polarity (tap to flip, red strikethrough) joined
  into a single `@keywords` group.

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
