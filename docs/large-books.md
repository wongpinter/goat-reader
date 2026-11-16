# Large-publication handling

Large EPUB packages can contain thousands of spine documents or resource entries. ZIP compression size alone does not predict parsing, pagination or memory cost.

## EPUB path

EPUBs open through Readium's `PublicationOpener`, `AssetRetriever` and navigator. The engine can use sparse locator positions for dense publications, reducing position-index growth. This remains a heuristic; it is not a strict memory or startup-time guarantee.

Import preflight bounds archive metadata, entry counts, individual resource sizes and selected navigation/package documents. The ZIP central directory is read before application-level entry-count validation, so these limits do not prove safety for every malformed or ZIP64 archive. See [EPUB conformance status](epub33-reading-systems-conformance.md).

## Other formats

Native parsing and pagination use bounded previews and caches where supported. A readable opening preview can appear while additional content is prepared. Cache limits and fallback parsing protect normal operation, while an unusually large source can still consume substantial time, memory and storage.

## Testing guidance

Use generated synthetic fixtures for routine regression tests. Keep personal or licensed large books out of source control and public screenshots. Performance results depend on the publication, device, cache state and build type; avoid publishing a single measurement as a general guarantee.
