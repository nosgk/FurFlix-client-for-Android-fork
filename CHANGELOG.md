# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Profile consistency batch**: the user page's watching/watchers tabs
  now show each card's real profile banner via a shared banner store
  (warmed in either the My-page watchlist or the user page, keyed by
  lowercase username); watchlist card backgrounds gain an Appearance
  setting pair — source (profile banner / latest-artwork thumbnail,
  whose resolution still follows the thumbnail-quality tier, without
  touching the profile page header banner) and a 0-20 dp gaussian blur
  slider (0 = sharp) — applied to the card background only; the
  My-page watchlist gets a local search over username, display name
  and note, and long-pressing a user card (haptic confirmed) opens a
  private local note editor shown under the name; the post detail
  title is translatable under a new Titles category (bilingual under
  the original or replacing it per display mode, with a manual toggle);
  the comment icon in the stats bar now also scrolls the page so the
  comments land on screen, in single-pane and two-pane layouts.
- **Download & gallery experience batch** (download-gallery-ux RFC): the
  downloads page renders saved images at their measured aspect ratio
  (grid/masonry/list modes with a shared column preference) so nothing is
  cropped anymore; a real download pipeline (serial queue, per-task
  progress bar with percent and live speed, failure states with a retry
  button, filename dedupe, correct extensions from URL or content type,
  MediaStore cleanup on failure, and orphan-free metadata) replaces the
  old fire-and-forget download, and the detail page's check icon now
  reflects the actual task state; long-press multi-select on the main
  galleries with batch favorite (serial, rate-aware) and batch download
  (detail-resolving two-stage tasks); user page gallery folders with an
  in-place folder browser and isolated pagination; gallery view modes and
  the paw pull-to-refresh applied to the user page and tag search;
  auto-hiding top/bottom bars with an Appearance switch; a text size
  setting (five steps multiplied over the system font scale); progressive
  hero loading (cached thumbnail placeholder, ratio-preset container, ring
  progress with percent and speed keyed per URL); automatic 3x retry with
  backoff for images and page fetches, surfacing raw error codes with a
  manual retry; neighbor-page prefetch and click-time prewarming for
  instant page turns; per-keyword search count chips (single-page fetch
  count, parallel); and zh-CN mirrors for every new string. Hardcoded
  user-page empty/error strings extracted into resources.

- **Browsing experience batch** (browse-ux RFC): user-adjustable gallery grid- **Browsing experience batch** (browse-ux RFC): user-adjustable gallery grid
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
- **Gallery stats for everything visible**: author-gallery tiles warm their
  stats as they scroll into view (serial, rate-limited detail prefetch that
  backfills the cache silently), so the overlay is no longer limited to
  previously seen submissions.
- **History & watchlist upgrades**: the viewing-history tab gains the shared
  gallery view modes (grid/masonry/list) and pull-to-refresh via a reusable
  `PawPullRefreshBox` (the HomeScreen pattern with fresh-callback safety);
  the watchlist tab gets pull-to-refresh too, and its user cards show each
  user's actual FA profile banner as the card background (per-visible-card
  profile prefetch, deduped per session).
- **Thread connectors track bilingual rows**: comment avatars pin to the row
  top so the elbow geometry stays valid when a bilingual translation expands
  the body; each row type passes its real top padding to the connector math
  (fixes the folded-row anchor drift), and the highlight pulse no longer
  paints over the lines.
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

### Fixed
- Viewing-history masonry degenerated into a uniform grid: rebuilding
  history entries from the stats cache dropped the parsed thumbnail
  ratio, so every card pre-sized to 1:1. The ratio is carried again,
  and previously cached entries recover immediately.
- Compact grid/masonry cards in the My-page favorites and viewing
  history hid their titles; they now show the one-line title like the
  same card everywhere else.

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
