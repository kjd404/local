from __future__ import annotations

import base64
import json
import os
import subprocess
from pathlib import Path
from typing import Callable, Mapping

try:
    from bazel_tools.tools.python.runfiles import runfiles
except ImportError:  # pragma: no cover - runtime guard for non-Bazel execution
    runfiles = None  # type: ignore[assignment]

from .errors import OcrProcessingError
from .interfaces import ReceiptImagePreprocessor, ReceiptOcrService
from .receipt_image import ReceiptImage
from .reporting import StepReporter, noop_reporter
from .result import OcrResult

Runner = Callable[..., subprocess.CompletedProcess[str]]


class BinaryOcrService(ReceiptOcrService):
    """Invokes the Bazel-selected OCR backend via JSON stdin/stdout."""

    def __init__(
        self,
        preprocessor: ReceiptImagePreprocessor,
        *,
        binary_path: str | os.PathLike[str] | None = None,
        runfile_location: str = "apps/plutary/ocr_service_binary",
        workspace_name: str | None = None,
        locale: str = "en_US",
        minimum_confidence: float = 0.3,
        recognition_level: str = "accurate",
        step_reporter: StepReporter | None = None,
        env: Mapping[str, str] | None = None,
        timeout_seconds: float | None = None,
        runner: Runner | None = None,
    ) -> None:
        self._preprocessor = preprocessor
        self._explicit_binary_path = Path(binary_path) if binary_path else None
        self._runfile_location = runfile_location
        alias_location = "apps/plutary/ocr_service_binary"
        candidates: list[str] = []
        if alias_location not in candidates:
            candidates.append(alias_location)
        if runfile_location != alias_location:
            candidates.append(runfile_location)
        for fallback in (
            "apps/plutary/mac_vision_ocr/ocr_service",
            "apps/plutary/src/plutary/ocr/paddle_ocr_service_bin",
        ):
            if fallback not in candidates:
                candidates.append(fallback)
        self._candidate_locations = candidates
        self._workspace_name = (
            workspace_name
            or os.environ.get("TEST_WORKSPACE")
            or os.environ.get("BAZEL_WORKSPACE")
            or ""
        )
        self._workspace_aliases = {
            alias
            for alias in {
                self._workspace_name,
                "",
                "__main__",
                "_main",
            }
            if alias is not None
        }
        self._locale = locale
        self._minimum_confidence = minimum_confidence
        self._recognition_level = recognition_level
        self._report = step_reporter or noop_reporter
        self._env = dict(env) if env else None
        self._timeout = timeout_seconds
        self._runner = runner or subprocess.run
        self._cached_binary_path: Path | None = self._explicit_binary_path
        self._backend_name = "unknown"
        if self._cached_binary_path is not None:
            self._backend_name = self._infer_backend(self._cached_binary_path)
        self._runfiles = runfiles.Create() if runfiles is not None else None

    @property
    def backend_name(self) -> str:
        """Human-friendly identifier for the active backend."""
        if self._cached_binary_path is None:
            try:
                self._resolve_binary_path()
            except OcrProcessingError:
                return "unknown"
        return self._backend_name

    def extract_text(self, image: ReceiptImage) -> OcrResult:
        if image is None:
            raise ValueError("image must not be None")
        if image.is_empty():
            raise OcrProcessingError("Receipt image bytes are empty.")

        self._report("preprocess", "Running receipt image preprocessor")
        processed = self._preprocessor.preprocess(image)

        payload = self._build_request(processed)
        binary_path = self._resolve_binary_path()
        self._report(
            "ocr",
            f"Invoking {self.backend_name} OCR backend ({binary_path.name})",
        )

        try:
            completed = self._runner(
                [str(binary_path)],
                input=payload,
                text=True,
                capture_output=True,
                env=self._env,
                timeout=self._timeout,
            )
        except subprocess.TimeoutExpired as exc:
            raise OcrProcessingError("OCR backend timed out") from exc
        except FileNotFoundError as exc:
            raise OcrProcessingError("OCR backend binary not found") from exc

        return self._handle_completion(completed)

    def _build_request(self, processed: bytes) -> str:
        request = {
            "image_png_base64": base64.b64encode(processed).decode("ascii"),
            "locale": self._locale,
            "minimum_confidence": self._minimum_confidence,
            "recognition_level": self._recognition_level,
        }
        return json.dumps(request)

    def _resolve_binary_path(self) -> Path:
        if self._cached_binary_path is not None:
            return self._cached_binary_path
        if self._runfiles is None:
            raise OcrProcessingError("Bazel runfiles helper is unavailable")

        search_order: list[str] = []
        for location in self._candidate_locations:
            for alias in self._workspace_aliases:
                if alias:
                    search_order.append(f"{alias}/{location}")
            search_order.append(location)

        for logical in search_order:
            resolved = self._runfiles.Rlocation(logical)
            if resolved:
                self._cached_binary_path = Path(resolved)
                self._backend_name = self._infer_backend(self._cached_binary_path)
                return self._cached_binary_path

        runfiles_dir = os.environ.get("RUNFILES_DIR")
        if runfiles_dir:
            for candidate in search_order:
                candidate_path = Path(runfiles_dir) / candidate
                if candidate_path.exists():
                    self._cached_binary_path = candidate_path
                    self._backend_name = self._infer_backend(candidate_path)
                    return candidate_path

        searched = ", ".join(search_order)
        raise OcrProcessingError(
            "Unable to locate OCR backend runfile; looked for: " + searched
        )

    def _infer_backend(self, path: Path) -> str:
        parts = {part.lower() for part in path.parts}
        name = path.name.lower()
        if "mac_vision_ocr" in parts or "vision" in name:
            return "vision"
        if "paddle" in parts or "paddle" in name:
            return "paddle"
        return "unknown"

    def _handle_completion(
        self, completed: subprocess.CompletedProcess[str]
    ) -> OcrResult:
        if completed.returncode == 0:
            try:
                payload = json.loads(completed.stdout)
            except json.JSONDecodeError as exc:
                raise OcrProcessingError("OCR backend returned invalid JSON") from exc

            text = payload.get("text")
            if not isinstance(text, str):
                raise OcrProcessingError("OCR response missing 'text' field")
            warnings = payload.get("warnings", [])
            if isinstance(warnings, list) and warnings:
                joined = ", ".join(str(item) for item in warnings)
                self._report("ocr", f"Backend reported warnings: {joined}")
            self._report("ocr", "OCR backend completed successfully")
            return OcrResult(text.strip())

        message = self._format_failure_message(completed)
        if completed.returncode in (2, 3):
            raise OcrProcessingError(message)
        if completed.returncode == 4:
            raise OcrProcessingError(f"Unexpected OCR backend error: {message}")
        raise OcrProcessingError(
            f"OCR backend exited with code {completed.returncode}: {message}"
        )

    def _format_failure_message(
        self, completed: subprocess.CompletedProcess[str]
    ) -> str:
        stderr = (completed.stderr or "").strip()
        stdout = (completed.stdout or "").strip()
        if stderr and stdout:
            return f"{stderr} | {stdout}"
        return stderr or stdout or "no diagnostics from backend"
