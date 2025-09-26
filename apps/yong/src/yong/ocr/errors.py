"""Domain-specific exceptions for OCR failures."""


class OcrProcessingError(RuntimeError):
    """Raised when OCR preprocessing or inference fails irrecoverably."""

    def __init__(self, message: str, *, cause: Exception | None = None) -> None:
        super().__init__(message)
        self.__cause__ = cause
