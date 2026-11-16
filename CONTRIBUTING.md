# Contributing

Contributions to GOAT Reader are welcome. Open an issue first for large changes so the expected behavior and scope are clear.

## Development setup

- JDK 21
- Android SDK Platform 37
- Android Gradle Plugin and Kotlin versions pinned in `gradle/libs.versions.toml`

Use the checked-in Gradle wrapper:

```sh
./gradlew test :app:assembleDebug
```

Run a focused test class with:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.wongpinter.goatreader.parser.EpubParserTest'
```

Release builds are unsigned unless external signing credentials are configured. See [the release guide](docs/releasing.md).

## Code and data guidelines

- Preserve existing storage and backup compatibility. New serialized fields need defaults and persistent writes must use the repository's atomic-storage abstractions.
- Keep parser work bounded for malformed or unusually large inputs; add small synthetic regression fixtures when practical.
- Do not commit personal ebooks, credentials, keystores, local settings, APKs, build output or screenshots containing private data.
- Add third-party assets only when their provenance and licence are documented in `THIRD_PARTY_NOTICES.md`.
- Use reproducible commands and report checks actually run. JVM tests and previews do not establish device behavior.

## Pull requests

1. Create a focused branch from `main`.
2. Include tests for behavior changes and update user/developer documentation where needed.
3. Run relevant tests and `:app:assembleDebug`; include release/lint checks when the change affects those paths.
4. Describe known limitations and manual verification separately from automated checks.

The project is licensed under GPL-3.0-only. Contributions must be compatible with that licence.
