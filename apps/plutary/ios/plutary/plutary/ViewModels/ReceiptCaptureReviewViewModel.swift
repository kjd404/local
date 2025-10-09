//
//  ReceiptCaptureReviewViewModel.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Combine
import Foundation
import OSLog
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

@MainActor
final class ReceiptCaptureReviewViewModel: ObservableObject, Identifiable {
    let id = UUID()

    @Published var merchantName: String
    @Published var purchaseDate: Date
    @Published var totalAmountText: String
    @Published var taxAmountText: String
    @Published var tipAmountText: String
    @Published var paymentMethod: String
    @Published var notes: String
    @Published var isSaving = false
    @Published var alertMessage: ReceiptsListViewModel.AlertState?

    let imageData: Data
    let recognizedText: String
    let recognizedLocale: String?
    private(set) var autoFilledFields: Set<ReceiptAutoFillField>

    var onComplete: (() -> Void)?

    private let receiptStore: ReceiptStoreProtocol
    private let imageStore: ReceiptImageStoreProtocol
    private let currencyFormatter: NumberFormatter

    init(
        imageData: Data,
        ocrResult: ReceiptOcrResult,
        receiptStore: ReceiptStoreProtocol,
        imageStore: ReceiptImageStoreProtocol,
        locale: Locale = .current
    ) {
        self.imageData = imageData
        self.recognizedText = ocrResult.rawText
        self.recognizedLocale = ocrResult.localeIdentifier
        self.receiptStore = receiptStore
        self.imageStore = imageStore

        currencyFormatter = NumberFormatter()
        currencyFormatter.numberStyle = .currency
        currencyFormatter.generatesDecimalNumbers = true
        if let code = ocrResult.candidates.currencyCode {
            currencyFormatter.currencyCode = code
        }
        currencyFormatter.locale = locale

        merchantName = ocrResult.candidates.merchantName ?? ""
        purchaseDate = ocrResult.candidates.purchaseDate ?? Date()
        totalAmountText = ReceiptCaptureReviewViewModel.format(decimal: ocrResult.candidates.totalAmount, with: currencyFormatter)
        taxAmountText = ReceiptCaptureReviewViewModel.format(decimal: ocrResult.candidates.taxAmount, with: currencyFormatter)
        tipAmountText = ReceiptCaptureReviewViewModel.format(decimal: ocrResult.candidates.tipAmount, with: currencyFormatter)
        paymentMethod = ""
        notes = ""

        var fields = Set<ReceiptAutoFillField>()
        if ocrResult.candidates.merchantName != nil { fields.insert(.merchantName) }
        if ocrResult.candidates.purchaseDate != nil { fields.insert(.purchaseDate) }
        if ocrResult.candidates.totalAmount != nil { fields.insert(.totalAmount) }
        if ocrResult.candidates.taxAmount != nil { fields.insert(.taxAmount) }
        if ocrResult.candidates.tipAmount != nil { fields.insert(.tipAmount) }
        autoFilledFields = fields
    }

    var saveEnabled: Bool {
        !merchantName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && decimal(from: totalAmountText) != nil
    }

    func normalizeCurrencyFields() {
        let total = decimal(from: totalAmountText)
        totalAmountText = ReceiptCaptureReviewViewModel.format(decimal: total, with: currencyFormatter)
        if total != nil {
            autoFilledFields.insert(.totalAmount)
        }

        let tax = decimal(from: taxAmountText)
        taxAmountText = ReceiptCaptureReviewViewModel.format(decimal: tax, with: currencyFormatter)
        if let _ = tax {
            autoFilledFields.insert(.taxAmount)
        } else {
            autoFilledFields.remove(.taxAmount)
        }

        let tip = decimal(from: tipAmountText)
        tipAmountText = ReceiptCaptureReviewViewModel.format(decimal: tip, with: currencyFormatter)
        if let _ = tip {
            autoFilledFields.insert(.tipAmount)
        } else {
            autoFilledFields.remove(.tipAmount)
        }
    }

    func save() {
        guard saveEnabled else {
            alertMessage = ReceiptsListViewModel.AlertState(message: "Please confirm merchant, total, and date before saving.")
            return
        }
        guard let total = decimal(from: totalAmountText) else {
            alertMessage = ReceiptsListViewModel.AlertState(message: "Total amount could not be parsed.")
            return
        }

        let merchant = merchantName.trimmingCharacters(in: .whitespacesAndNewlines)
        let tax = decimal(from: taxAmountText)
        let tip = decimal(from: tipAmountText)
        let normalizedPayment = paymentMethod.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedNotes = notes.trimmingCharacters(in: .whitespacesAndNewlines)

        isSaving = true
        Task {
            do {
                let payload = normalizedImageData()
                let token = try imageStore.storeImageData(payload, preferredToken: nil)
                let now = Date()
                let receipt = Receipt(
                    merchantName: merchant,
                    purchaseDate: purchaseDate,
                    totalAmount: total,
                    taxAmount: tax,
                    tipAmount: tip,
                    paymentMethod: normalizedPayment.isEmpty ? nil : normalizedPayment,
                    notes: normalizedNotes.isEmpty ? nil : normalizedNotes,
                    imageToken: token,
                    ocrText: recognizedText,
                    ocrLocaleIdentifier: recognizedLocale,
                    createdAt: now,
                    updatedAt: now,
                    lineItems: [],
                    autoFilledFields: autoFilledFields
                )
                try await receiptStore.upsert(receipt)
                await MainActor.run {
                    self.isSaving = false
                    self.onComplete?()
                }
            } catch {
                AppLogger.ocr.error("Captured receipt save failed: \(error.localizedDescription, privacy: .public)")
                await MainActor.run {
                    self.isSaving = false
                    self.alertMessage = ReceiptsListViewModel.AlertState(message: "We couldn't save your receipt. \(error.localizedDescription)")
                }
            }
        }
    }

    // MARK: - Helpers

    private func decimal(from text: String) -> Decimal? {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        if let number = currencyFormatter.number(from: text) as? NSDecimalNumber {
            return number.decimalValue
        }
        return Decimal(string: text.filter { "0123456789.,-".contains($0) }, locale: currencyFormatter.locale)
    }

    private static func format(decimal: Decimal?, with formatter: NumberFormatter) -> String {
        guard let decimal else { return "" }
        return formatter.string(from: NSDecimalNumber(decimal: decimal)) ?? ""
    }
}

extension ReceiptCaptureReviewViewModel {
    private func normalizedImageData() -> Data {
        #if canImport(UIKit)
        if let uiImage = UIImage(data: imageData), let jpeg = uiImage.jpegData(compressionQuality: 0.8) {
            return jpeg
        }
        #endif
        return imageData
    }
}
