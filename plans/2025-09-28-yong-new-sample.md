# Yong Sample Receipt Integration (IMG_4395)

## Overview / Goal
- Relocate the new receipt photo (`IMG_4395.jpeg`) into `apps/yong/src/test/resources/examples/` so it ships with the Yong test assets.
- Capture canonical metadata for the photo using an OpenAI (or agent-accessible) vision model and wire it into the ground-truth fixtures and regression tests so the new sample exercises the OCR + parser pipeline.

## Current State
- The new photo lives at the repo root as `IMG_4395.jpeg`, so it is not bundled into the Bazel `example_receipts` data set (`apps/yong/BUILD.bazel:6-13`).
- Regression tests iterate over every JSON fixture under `src/test/resources/ground_truth/` (`apps/yong/tests/receipts/test_ground_truth_pipeline.py:43-112`), but only samples 01–04 have curated expectations today.
- Each existing fixture pairs a JSON file and JPEG with matching stems (e.g., `sample_receipt_photo_04`), and the JSON carries full transaction + OCR context used by the pipeline test (`apps/yong/src/test/resources/ground_truth/sample_receipt_photo_04.json:1`).

## Proposed Changes
1. **Relocate and Rename the Image**
   - Move `IMG_4395.jpeg` into `apps/yong/src/test/resources/examples/` and rename it to follow the established `sample_receipt_photo_0X.jpeg` pattern (next index is `05`).
   - Verify the Bazel `filegroup` continues to pick it up (no BUILD changes expected because of the glob) and ensure the Git history records the rename and move.

2. **Author Ground-Truth Fixture via Vision Model**
   - Invoke an OpenAI (or agent-native) vision model on the relocated image to extract structured text: merchant details, totals, line items, payments, and the raw OCR text block.
   - Normalize the model output, cross-check every field against the image manually, and resolve discrepancies by re-prompting or hand-correcting—aim for parity with the fields present in existing fixtures.
   - Create `apps/yong/src/test/resources/ground_truth/sample_receipt_photo_05.json` mirroring the schema in current fixtures. Populate `ocr_text` with the verbatim multiline transcription and set `expected_transaction.raw_json` to the structured payload derived from the vision model, adjusting for any formatting the parser expects.

3. **Extend Regression Expectations**
   - Update `SAMPLE_EXPECTATIONS` in `apps/yong/tests/receipts/test_ground_truth_pipeline.py:47-63` with a new entry for `sample_receipt_photo_05`, capturing a representative line item (description, amount, quantity if known).
   - Ensure any docs that enumerate sample IDs (README CLI examples at `apps/yong/README.md:29-55`) reference the new sample where it improves coverage (e.g., mention it alongside other dry-run options).

4. **Housekeeping & Repository Hygiene**
   - Confirm `apps/yong/src/test/resources/examples/` and `ground_truth/` stay alphabetically ordered; update `.gitignore` or scripts only if new tooling was required (not expected).
   - Capture a short note in `apps/yong/README.md` or `AGENTS.md` if the sample highlights a new parsing edge case uncovered by the vision model transcription.

## Testing & Verification
- `bazel test //apps/yong:yong_tests`
- Manual validation: run `bazel run //apps/yong:receipt_cli -- --image=apps/yong/src/test/resources/examples/sample_receipt_photo_05.jpeg --dry-run` to confirm the pipeline aligns with the new fixture (optional but recommended).
- Review the JSON fixture to ensure the vision model transcription matches the receipt image.

## Risks & Mitigations
- **Incorrect ground truth data**: carefully reconcile the vision model output with the photo; if uncertain, iterate with targeted prompts or fall back to manual transcription.
- **Parser gaps exposed by new sample**: be prepared to file follow-up tasks if heuristics fail; the plan only introduces the sample and fixture, not parser changes.
- **Large image size**: if the JPEG is significantly larger than existing samples, consider downscaling while preserving readability to keep repository size manageable.

## Out of Scope
- Enhancing OCR or parser heuristics beyond discrepancies uncovered by the new sample.
- Automating fixture generation or modifying database-backed tests.

## Success Criteria
### Automated Verification
- [x] `bazel test //apps/yong:yong_tests`

### Manual Verification
- [x] Vision model transcription recorded in `sample_receipt_photo_05.json` matches the receipt (merchant, totals, line items).
- [x] CLI dry run against `sample_receipt_photo_05.jpeg` completes with `ReceiptProcessingStatus.COMPLETED` and matches the new fixture totals.
- [x] Visual spot-check confirms the JSON fixture’s merchant, total, and at least one line item align with the photo.
