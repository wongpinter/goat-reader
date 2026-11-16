# Architecture

GOAT Reader is a native, offline-first Android application built with Kotlin, Jetpack Compose and Material 3. The application has no required account or server dependency.

## Layers

- **Compose UI:** Library, reader, profile, settings and onboarding screens.
- **View models:** UI state, user actions, reading progress and validation.
- **Repositories:** Library, settings, backup and reading-history operations.
- **Storage:** Atomic JSON-backed library/user data, progress, settings and caches. In-app DATA/FULL archives provide portable backups.
- **Reading engines:** Readium Kotlin Toolkit powers EPUB navigation; native parsers and Compose rendering handle FB2, MOBI/KF8, TXT and Markdown.

## Reading flow

The library imports supported files into app-private storage and derives metadata through the relevant parser. EPUB files open through `EpubEngine` and the Readium navigator. Other supported formats use the shared native content model and Compose reader. Reading position, annotations and user-created history are persisted through separate data paths to limit interference between frequent progress writes and user data.

Publisher CSS, MathML, ruby and fixed-layout behavior have format-specific limits. See [EPUB rendering notes](docs/epub-standard-ebooks-audit.md) and the [EPUB 3.3 conformance status](docs/epub33-reading-systems-conformance.md) for scope and open gaps.

## Persistence and backup

Persistent writes use atomic replacement to protect existing data from partial writes. Library metadata, user-created annotations/settings and reading progress are stored separately. Backup archives are versioned independently of the internal storage layout; additive model changes retain defaults for older data.

Custom fonts, extracted resources, parser snapshots and pagination use bounded caches. User book files remain in app-private storage. Optional file access and external text actions are mediated by Android's document and intent APIs.

## Build configuration

`app/build.gradle.kts` defines the Android application, SDK levels and release signing behavior. Dependency versions are pinned in `gradle/libs.versions.toml`; `version.properties` controls release name and code. Normal release builds are unsigned unless external signing credentials are configured.

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup and verification commands.
