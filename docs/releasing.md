# Release guide

## Prepare a release

1. Update `version.properties` deliberately; Android version codes must increase.
2. Review the full source and staged paths. Keep local books, credentials, keystores, APKs, build output and private screenshots out of Git.
3. Update `CHANGELOG.md`, the README and a release note. Record only checks actually performed.
4. Run `./gradlew test :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease`.
5. Verify the release APK with `apksigner verify --verbose --print-certs`, inspect the application ID/version/SDK levels, and record its SHA-256.
6. Review GPL-3.0-only and third-party licence/source obligations before distributing an APK.
7. Push reviewed commits and tags only after the repository destination is confirmed.

## Signing

`assembleRelease` is unsigned by default. Configure these environment variables outside the checkout to sign with an external key:

- `GOATREADER_KEYSTORE_PATH`
- `GOATREADER_KEYSTORE_PASSWORD`
- `GOATREADER_KEY_ALIAS`
- `GOATREADER_KEY_PASSWORD`

Do not store keystores, passwords or signing keys in Git. A local Android Debug certificate is for review and test installation only; use a controlled production signing key for public distribution and preserve it securely for future updates.
