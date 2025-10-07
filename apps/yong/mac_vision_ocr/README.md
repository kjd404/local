# Yong macOS Vision OCR Service

A Swift command-line binary that uses Apple's Vision framework to satisfy Yong's OCR requests.
The executable consumes a JSON payload on stdin and emits a JSON response on stdout so the Python
pipeline (and Bazel tests) can treat it interchangeably with the Paddle backend.

## Prerequisites
- macOS 12 or newer with the Xcode command line tools installed (`xcode-select --install`).
- Bazelisk/Bazel 7.1.2 or newer (pinned in `.bazelversion`).
- Vision, CoreGraphics, and ImageIO frameworks are provided by the host SDK.

## Build & Test
```
bazel build //apps/yong/mac_vision_ocr:ocr_service
bazel test //apps/yong/mac_vision_ocr:ocr_service_golden_test
```
- The golden test automatically skips when executed on non-Darwin hosts.

## Usage
```
PYTHONPATH=apps/yong/src python3 - <<'PY' > /tmp/vision_request.json
import base64, json
from pathlib import Path
from yong.ocr import DefaultReceiptImagePreprocessor, ReceiptImage
from yong.ocr.reporting import noop_reporter

image_path = Path("apps/yong/src/test/resources/examples/sample_receipt_photo_01.jpeg")
preprocessor = DefaultReceiptImagePreprocessor(reporter=noop_reporter)
image_bytes = image_path.read_bytes()
processed = preprocessor.preprocess(ReceiptImage(image_bytes, content_type="image/jpeg"))
payload = {
    "image_png_base64": base64.b64encode(processed).decode("ascii"),
    "locale": "en_US",
    "minimum_confidence": 0.3,
    "recognition_level": "accurate",
}
print(json.dumps(payload))
PY
bazel run //apps/yong/mac_vision_ocr:ocr_service < /tmp/vision_request.json
```
- The binary prints a JSON response with `text` and optional `warnings`. Validation errors exit with
  code `2`, Vision execution problems use `3`, and unexpected failures return `4`.
- JSON responses are stored under `apps/yong/tests/ocr/golden/` for regression coverage.

## Layout
- `BUILD.bazel` – Bazel targets (`swift_library`, `macos_command_line_application`, and golden test`).
- `Sources/MacVisionOcr/` – Swift sources implementing the JSON contract and Vision integration.
- `vision_cli_smoke_test.sh` – Platform-aware golden harness that exercises the binary against
  bundled fixtures.
