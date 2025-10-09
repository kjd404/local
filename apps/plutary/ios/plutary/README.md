# Plutary iOS MVP

SwiftUI client for local-first receipt ingestion with manual entry, photo capture, and on-device
OCR. The app persists everything in an in-memory-friendly Core Data stack and exposes clean seams
for repository injection and future backend sync.

## Building & Running

```
xcodebuild -project apps/plutary/ios/plutary/plutary.xcodeproj \
  -scheme Plutary -destination 'platform=iOS Simulator,name=iPhone 17' run
```

Or open `plutary.xcodeproj` in Xcode 16 and target an iOS 17/18 simulator or device.

Useful launch arguments:

- `--uitests` – instructs `PlutaryApp` to spin up an in-memory store and seed fixtures.
- `--fixture <name>` – chooses which fixture to load (`sample`, `empty`).

## Seed Fixtures (UI Tests)

`UITestSeeder` replaces the store contents through the `ReceiptStoreProtocol`, ensuring tests never
depend on stale Core Data state. You can reuse it locally by launching the app with the arguments
above. Fixtures live inline today, but the seeding helper accepts any `Receipt` array, so adding new
scenarios is trivial.

## Manual & Camera Capture

`ReceiptCaptureFlowView` now offers both an import picker and a camera sheet (when hardware is
available). Captured images are compressed to JPEG before persisting via `ReceiptImageStoreProtocol`
so we can later swap storage implementations if needed.

## Payload Structure (future backend sync)

When we sync receipts upstream, each record will follow this shape:

```json
{
  "id": "UUID",
  "merchant_name": "string",
  "purchase_date": "ISO8601 timestamp",
  "total_amount": "decimal string",
  "tax_amount": "decimal string | null",
  "tip_amount": "decimal string | null",
  "payment_method": "string | null",
  "notes": "string | null",
  "image_token": "string | null",
  "ocr_text": "string | null",
  "ocr_locale": "BCP-47 locale | null",
  "auto_filled_fields": ["merchant_name", "total_amount", ...],
  "line_items": [
    {
      "id": "UUID",
      "description": "string",
      "quantity": "decimal string | null",
      "unit_price": "decimal string | null",
      "total": "decimal string | null"
    }
  ],
  "created_at": "ISO8601 timestamp",
  "updated_at": "ISO8601 timestamp"
}
```

Images continue to live on device; only the `image_token` travels upstream. OCR text is stored
verbatim so the backend can re-run improved parsers later.

## Testing

Unit & UI suites are driven through `xcodebuild`:

```
xcodebuild -project apps/plutary/ios/plutary/plutary.xcodeproj \
  -scheme Plutary -destination 'platform=iOS Simulator,name=iPhone 17' test
```

Key coverage:
- `PlutaryTests.swift`: manual form validation, Core Data CRUD, OCR heuristics, and integration
  coverage for the capture review flow.
- `PlutaryUITests.swift`: fixture-backed list assertions plus manual entry smoke test.

Remember to update `plans/2025-09-28-plutary-ios-mvp.md` if you extend flows or add new fixtures.
