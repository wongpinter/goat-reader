# Build and test

Use JDK 21, the Android SDK Platform 37 and the checked-in Gradle wrapper. Dependency versions are managed through `gradle/libs.versions.toml`.

## Common checks

```sh
./gradlew test
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

For the optimized release variant:

```sh
./gradlew :app:assembleRelease :app:lintRelease
```

A release build is unsigned by default. Configure the documented `GOATREADER_KEYSTORE_*` environment variables outside the checkout to sign with an external key. Never add private signing material to the repository.

## Verification boundaries

JVM tests cover deterministic parser, storage and UI-state behavior. Instrumentation tests and manual device checks are needed for Android framework integration, WebView behavior, gestures, TalkBack and rendered layouts. Compose previews are useful fixtures, not substitutes for inspecting device screenshots.

Use small synthetic or redistributable book fixtures in public tests. Local user books and performance corpora remain outside source control and are excluded by the repository ignore rules.
