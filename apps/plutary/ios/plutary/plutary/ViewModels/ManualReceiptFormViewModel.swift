//
//  ManualReceiptFormViewModel.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Combine
import Foundation
import OSLog
import SwiftUI

@MainActor
final class ManualReceiptFormViewModel: ObservableObject, Identifiable {
    let id = UUID()
    struct LineItemDraft: Identifiable, Equatable {
        let id: UUID
        var description: String
        var quantityText: String
        var unitPriceText: String
        var totalText: String

        init(
            id: UUID = UUID(),
            description: String = "",
            quantityText: String = "",
            unitPriceText: String = "",
            totalText: String = ""
        ) {
            self.id = id
            self.description = description
            self.quantityText = quantityText
            self.unitPriceText = unitPriceText
            self.totalText = totalText
        }
    }

    @Published var merchantName: String = ""
    @Published var purchaseDate: Date = Date()
    @Published var totalAmountText: String = ""
    @Published var taxAmountText: String = ""
    @Published var tipAmountText: String = ""
    @Published var paymentMethod: String = ""
    @Published var notes: String = ""
    @Published var lineItems: [LineItemDraft] = []
    @Published var isSaving = false
    @Published var alertMessage: ReceiptsListViewModel.AlertState?

    var onSaveSuccess: (() -> Void)?

    private let receiptStore: ReceiptStoreProtocol
    private let currencyFormatter: NumberFormatter

    init(receiptStore: ReceiptStoreProtocol, calendar: Calendar = .current, locale: Locale = .current) {
        self.receiptStore = receiptStore
        currencyFormatter = NumberFormatter()
        currencyFormatter.numberStyle = .currency
        currencyFormatter.generatesDecimalNumbers = true
        currencyFormatter.locale = locale
    }

    var saveEnabled: Bool {
        guard let total = decimal(from: totalAmountText) else {
            return false
        }
        let merchant = merchantName.trimmingCharacters(in: .whitespacesAndNewlines)
        return !merchant.isEmpty && total > 0
    }

    func addLineItem() {
        lineItems.append(LineItemDraft())
    }

    func removeLineItems(at offsets: IndexSet) {
        lineItems.remove(atOffsets: offsets)
    }

    func formatCurrencyField(for field: inout String) {
        guard
            let decimalValue = decimal(from: field),
            let formatted = currencyFormatter.string(from: NSDecimalNumber(decimal: decimalValue))
        else {
            field = ""
            return
        }
        field = formatted
    }

    func save() {
        guard saveEnabled else {
            alertMessage = ReceiptsListViewModel.AlertState(message: "Please fill in the required fields before saving.")
            return
        }

        let total = decimal(from: totalAmountText) ?? 0
        let tax = decimal(from: taxAmountText)
        let tip = decimal(from: tipAmountText)
        let filteredLineItems = lineItems.compactMap { draft -> ReceiptLineItem? in
            let description = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !description.isEmpty else { return nil }
            return ReceiptLineItem(
                description: description,
                quantity: decimal(from: draft.quantityText),
                unitPrice: decimal(from: draft.unitPriceText),
                total: decimal(from: draft.totalText)
            )
        }

        isSaving = true
        Task {
            do {
                let draft = ManualReceiptDraft(
                    merchantName: merchantName,
                    purchaseDate: purchaseDate,
                    totalAmount: total,
                    taxAmount: tax,
                    tipAmount: tip,
                    paymentMethod: paymentMethod,
                    notes: notes,
                    lineItems: filteredLineItems
                )
                _ = try await receiptStore.createManualReceipt(draft)
                await MainActor.run {
                    self.isSaving = false
                    self.onSaveSuccess?()
                }
            } catch {
                AppLogger.receipts.error("Manual receipt save failed: \(error.localizedDescription, privacy: .public)")
                await MainActor.run {
                    self.isSaving = false
                    self.alertMessage = ReceiptsListViewModel.AlertState(message: "Unable to save receipt right now. \(error.localizedDescription)")
                }
            }
        }
    }

    func normalizeCurrencyFields() {
        formatCurrencyField(for: &totalAmountText)
        formatCurrencyField(for: &taxAmountText)
        formatCurrencyField(for: &tipAmountText)
        for index in lineItems.indices {
            formatCurrencyField(for: &lineItems[index].unitPriceText)
            formatCurrencyField(for: &lineItems[index].totalText)
        }
    }

    // MARK: - Private

    private func decimal(from text: String) -> Decimal? {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        if let number = currencyFormatter.number(from: text) as? NSDecimalNumber {
            return number.decimalValue
        }
        return Decimal(string: text.filter { "0123456789.,-".contains($0) }, locale: currencyFormatter.locale)
    }
}
