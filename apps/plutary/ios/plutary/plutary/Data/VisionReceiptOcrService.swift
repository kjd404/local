//
//  VisionReceiptOcrService.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Foundation
#if canImport(UIKit)
import UIKit
#endif
import Vision

final class VisionReceiptOcrService: ReceiptOcrService {
    func recognizeText(in imageData: Data) async throws -> ReceiptOcrResult {
        #if canImport(UIKit)
        guard let image = UIImage(data: imageData)?.cgImage else {
            throw ReceiptStoreError.validationFailed("Provided image data could not be decoded.")
        }

        let handler = VNImageRequestHandler(cgImage: image, options: [:])
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.revision = VNRecognizeTextRequestRevision3
        request.usesLanguageCorrection = true

        try handler.perform([request])
        guard let observations = request.results else {
            return ReceiptOcrResult(rawText: "", localeIdentifier: nil, candidates: ReceiptOcrCandidates())
        }

        let lines = observations.compactMap { $0.topCandidates(1).first?.string }
        let combinedText = lines.joined(separator: "\n")
        let detectedLocale = request.recognitionLanguages.first

        let candidates = inferCandidates(from: lines)

        return ReceiptOcrResult(
            rawText: combinedText,
            localeIdentifier: detectedLocale,
            candidates: candidates
        )
        #else
        throw ReceiptStoreError.validationFailed("OCR is not supported on this platform.")
        #endif
    }

    // MARK: - Candidate heuristics

    func inferCandidates(from lines: [String]) -> ReceiptOcrCandidates {
        var candidates = ReceiptOcrCandidates()
        let cleanedLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }

        candidates.merchantName = cleanedLines.first(where: { !$0.isEmpty && !containsKeyword($0, keywords: ["total", "subtotal", "amount due"]) })
        candidates.purchaseDate = detectDate(in: cleanedLines)

        candidates.totalAmount = detectAmount(in: cleanedLines, priorityKeywords: ["total", "amount due", "balance"])
        candidates.taxAmount = detectAmount(in: cleanedLines, priorityKeywords: ["tax"])
        candidates.tipAmount = detectAmount(in: cleanedLines, priorityKeywords: ["tip", "gratuity"])
        candidates.currencyCode = detectCurrencyCode(in: cleanedLines)

        return candidates
    }

    func containsKeyword(_ string: String, keywords: [String]) -> Bool {
        let lower = string.lowercased()
        return keywords.contains { keyword in
            let escaped = NSRegularExpression.escapedPattern(for: keyword.lowercased())
            let pattern = "\\b" + escaped.replacingOccurrences(of: " ", with: "\\s+") + "\\b"
            return lower.range(of: pattern, options: .regularExpression) != nil
        }
    }

    func detectDate(in lines: [String]) -> Date? {
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.date.rawValue)
        for line in lines {
            guard let match = detector?.firstMatch(in: line, options: [], range: NSRange(location: 0, length: line.utf16.count)) else { continue }
            if let date = match.date {
                return date
            }
        }
        return nil
    }

    func detectAmount(in lines: [String], priorityKeywords: [String]) -> Decimal? {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = localeFromCurrency(lines: lines)
        formatter.generatesDecimalNumbers = true

        let prioritized = lines.filter { containsKeyword($0, keywords: priorityKeywords) }
        if let amount = firstAmount(in: prioritized, formatter: formatter) {
            return amount
        }

        return firstAmount(in: lines, formatter: formatter)
    }

    func firstAmount(in lines: [String], formatter: NumberFormatter) -> Decimal? {
        for line in lines {
            let cleaned = line.replacingOccurrences(of: " ", with: "")
            if let amount = amountFromString(cleaned, formatter: formatter) {
                return amount
            }
        }
        return nil
    }

    func amountFromString(_ string: String, formatter: NumberFormatter) -> Decimal? {
        if let number = formatter.number(from: string) as? NSDecimalNumber {
            return number.decimalValue
        }
        let pattern = "[\\$£€]?\\s?(-?\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?)"
        if let regex = try? NSRegularExpression(pattern: pattern, options: []) {
            let range = NSRange(location: 0, length: string.utf16.count)
            if let match = regex.firstMatch(in: string, options: [], range: range) {
                let matched = (string as NSString).substring(with: match.range(at: 1))
                let normalized = matched.replacingOccurrences(of: ",", with: formatter.decimalSeparator == "." ? "" : ".")
                return Decimal(string: normalized, locale: formatter.locale) ?? Decimal(string: normalized.replacingOccurrences(of: formatter.decimalSeparator ?? ".", with: "."))
            }
        }
        return nil
    }

    func detectCurrencyCode(in lines: [String]) -> String? {
        if lines.contains(where: { $0.contains("€") }) { return "EUR" }
        if lines.contains(where: { $0.contains("£") }) { return "GBP" }
        if lines.contains(where: { $0.contains("$") }) { return "USD" }
        return nil
    }

    func localeFromCurrency(lines: [String]) -> Locale {
        if let code = detectCurrencyCode(in: lines) {
            return Locale(identifier: Locale.identifier(fromComponents: [NSLocale.Key.currencyCode.rawValue: code]))
        }
        return Locale.current
    }
}
