import os

enum AppLogger {
    static let receipts = Logger(subsystem: "org.artificers.plutary", category: "Receipts")
    static let ocr = Logger(subsystem: "org.artificers.plutary", category: "OCR")
}
