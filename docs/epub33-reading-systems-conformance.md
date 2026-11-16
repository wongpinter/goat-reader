# EPUB 3.3 conformance status

GOAT Reader uses Readium Kotlin Toolkit 3.2.0 for EPUB navigation. Some parser and resource-handling behaviors have focused regression tests; full EPUB 3.3 and EPUB Reading Systems 3.3 conformance has not been established.

## References

- [EPUB 3.3](https://www.w3.org/TR/epub-33/)
- [EPUB Reading Systems 3.3](https://www.w3.org/TR/epub-rs-33/)
- [EPUB 3.3 test suite](https://w3c.github.io/epub-tests/epub33/)

## Verified slices

- Legacy package parsing prevents external-entity resolution and handles declared manifest fallbacks.
- Bounded image data URLs and package metadata direction/language have focused JVM regression coverage.
- Readium's XML parser preserves namespace identities; a targeted test checks that an external DTD sentinel is not exposed by its parser path.

These tests establish only their named behavior. They do not prove full reading-system conformance or all active Android WebView behavior.

## Open verification work

- Exercise top-level `data:` navigation and explicit user-initiated handling against the active navigator.
- Verify that WebView cannot access resources through `file:` URLs, including requests that bypass Readium's hosted-resource interception.
- Complete package XML, XHTML and SVG processor checks on-device.
- Audit OCF ZIP/ZIP64 and malformed-container cases, URL handling, fallbacks, navigation, accessibility, privacy and applicable conditional requirements.
- Run the applicable normative test inventory and document supported optional capabilities and required fallbacks.

Readium's configured HTTP-client policy alone does not demonstrate how all WebView-initiated requests are handled. The current state is an implementation and audit in progress, not an EPUB 3.3 conformance claim.

See [EPUB rendering notes](epub-standard-ebooks-audit.md) for native-renderer capabilities and known limits.
