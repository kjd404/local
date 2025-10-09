//
//  ReceiptDetailViewModel.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Combine
import Foundation

@MainActor
final class ReceiptDetailViewModel: ObservableObject {
    @Published private(set) var receipt: Receipt

    private let currencyFormatter: NumberFormatter
    private let dateFormatter: DateFormatter

    init(receipt: Receipt, locale: Locale = .current) {
        self.receipt = receipt
        currencyFormatter = NumberFormatter()
        currencyFormatter.numberStyle = .currency
        currencyFormatter.generatesDecimalNumbers = true
        currencyFormatter.locale = locale

        dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .medium
        dateFormatter.timeStyle = .none
        dateFormatter.locale = locale
    }

    var merchantName: String {
        receipt.merchantName
    }

    var formattedPurchaseDate: String {
        dateFormatter.string(from: receipt.purchaseDate)
    }

    var formattedTotal: String {
        currencyFormatter.string(from: NSDecimalNumber(decimal: receipt.totalAmount)) ?? ""
    }

    var formattedTax: String? {
        guard let tax = receipt.taxAmount else { return nil }
        return currencyFormatter.string(from: NSDecimalNumber(decimal: tax))
    }

    var formattedTip: String? {
        guard let tip = receipt.tipAmount else { return nil }
        return currencyFormatter.string(from: NSDecimalNumber(decimal: tip))
    }

    func update(receipt: Receipt) {
        self.receipt = receipt
    }
}
