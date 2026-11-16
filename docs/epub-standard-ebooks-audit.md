# EPUB rendering notes

## Active reading path

The app opens EPUB publications with Readium Kotlin Toolkit 3.2.0 (`EpubEngine`) and hosts its `EpubNavigatorFragment` in `EpubReaderActivity`. EPUB content is rendered by the active Readium navigator/WebView; reader appearance controls and reading-position restoration are integrated with that flow. The legacy native-parser tests and audit below describe a separate historical implementation and do not establish the active navigator's rendering behavior.

## Historical native-renderer audit

An earlier audit exercised six Standard Ebooks editions. It recorded native-renderer work for structural CSS and whitespace, viewport-bounded artwork, verse and table layouts, embedded fonts, hidden alignment text, simple ruby, and a bounded MathML subset. Its corpus included 125 MathML expressions and 1,518 dialogue-table rows. These results document the legacy native path only; the complete EPUB packages are not included in this repository.

## Active-path limits and verification

- Publisher CSS, fixed-layout pages, vertical writing, MathML/ruby and complex inline layout may vary by publication and Readium/WebView behavior. The legacy audit is not evidence of parity for these features in the current EPUB reader.
- Top-level `data:` handling, `file:` resource isolation, and XHTML/SVG XML processing still need targeted Android-device verification.
- ZIP64 and malformed-container cases, bounded work for unusually large publications, and applicable optional-capability fallbacks remain under audit.
- Focused JVM tests establish only their named parser behaviors; they do not establish complete visual fidelity or EPUB 3.3 conformance.

See [EPUB 3.3 conformance status](epub33-reading-systems-conformance.md) for the current open checks and applicable standards.
