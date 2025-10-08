# Agent Notes – macOS Vision OCR CLI

## Ownership & Expectations
- Treat this directory as a standalone macOS-only utility; keep dependencies limited to Swift
  standard library and Apple frameworks (Vision/CoreGraphics/ImageIO).
- Preserve the command-line surface area (stdout only, no persistence). Add new flags only when they
  aid experimentation and keep defaults backward compatible.

## Development Checklist
- Build with `bazel build //apps/plutary/mac_vision_ocr:ocr_service` on macOS hosts.
- Run the golden contract test locally (`bazel test //apps/plutary/mac_vision_ocr:ocr_service_golden_test`). The
  shell harness skips automatically when not on Darwin; do not force-enable it elsewhere.
- Keep the CLI output human-readable—callers expect raw text they can diff against other OCR
  backends.
- Document any additional prerequisites or Vision-specific caveats in `README.md`.

## Future Enhancements
- Consider a thin wrapper target (alias or script) once the Python ingestion pipeline consumes these
  results.
- Evaluate optional locale detection or confidence reporting if product teams request it.
