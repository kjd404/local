#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Skipping macOS Vision smoke test: host is not macOS" >&2
  exit 0
fi

# shellcheck source=/dev/null
if [[ -d "${RUNFILES_DIR:-}" ]]; then
  # shellcheck source=/dev/null
  source "${RUNFILES_DIR}/bazel_tools/tools/bash/runfiles/runfiles.bash"
elif [[ -f "${RUNFILES_MANIFEST_FILE:-}" ]]; then
  manifest_path="$(grep -m1 '^bazel_tools/tools/bash/runfiles/runfiles.bash ' "${RUNFILES_MANIFEST_FILE}" | cut -d ' ' -f 2 || true)"
  if [[ -z "${manifest_path}" ]]; then
    echo "Unable to locate Bazel runfiles helper" >&2
    exit 1
  fi
  # shellcheck source=/dev/null
  source "${manifest_path}"
else
  echo "Unable to locate Bazel runfiles helper" >&2
  exit 1
fi

workspace="${TEST_WORKSPACE:-}"
if [[ -z "${workspace}" ]]; then
  echo "TEST_WORKSPACE is not set" >&2
  exit 1
fi

binary_runfile="${workspace}/apps/plutary/mac_vision_ocr/ocr_service"
expected_runfile="${workspace}/apps/plutary/tests/ocr/golden/sample_receipt_photo_01_response_vision.json"
image_runfile="${workspace}/apps/plutary/src/test/resources/examples/sample_receipt_photo_01.jpeg"

binary_path="$(rlocation "${binary_runfile}")"
expected_path="$(rlocation "${expected_runfile}")"
image_path="$(rlocation "${image_runfile}")"

if [[ ! -x "${binary_path}" ]]; then
  echo "macOS Vision OCR binary not found in runfiles: ${binary_path}" >&2
  exit 1
fi

if [[ ! -f "${expected_path}" ]]; then
  echo "Golden OCR response missing: ${expected_path}" >&2
  exit 1
fi

if [[ ! -f "${image_path}" ]]; then
  echo "Sample receipt image missing: ${image_path}" >&2
  exit 1
fi

temp_actual="$(mktemp -t vision_actual).json"
temp_request="$(mktemp -t vision_request).json"
trap 'rm -f "${temp_actual}" "${temp_request}"' EXIT

request_runfile="${workspace}/apps/plutary/tests/ocr/golden/sample_receipt_photo_01_request_vision.json"
request_path="$(rlocation "${request_runfile}")"
if [[ ! -f "${request_path}" ]]; then
  echo "Vision OCR request fixture missing: ${request_path}" >&2
  exit 1
fi
cp "${request_path}" "${temp_request}"

if ! "${binary_path}" < "${temp_request}" > "${temp_actual}"; then
  echo "Vision OCR binary failed" >&2
  exit 1
fi

python3 - "$expected_path" "$temp_actual" <<'PY'
import json
import sys
from pathlib import Path

expected = json.loads(Path(sys.argv[1]).read_text())
actual = json.loads(Path(sys.argv[2]).read_text())

if expected != actual:
    print("Vision OCR output mismatched golden fixture", file=sys.stderr)
    print("Expected:", json.dumps(expected, indent=2, ensure_ascii=False), file=sys.stderr)
    print("Actual:", json.dumps(actual, indent=2, ensure_ascii=False), file=sys.stderr)
    sys.exit(1)
PY

exit 0
