//
//  ReceiptRowView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import SwiftUI

struct ReceiptRowView: View {
    let receipt: Receipt

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

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(receipt.merchantName)
                    .font(.headline)
                Spacer()
                Text(currencyFormatter.string(from: NSDecimalNumber(decimal: receipt.totalAmount)) ?? "")
                    .font(.headline)
            }
            HStack {
                Text(dateFormatter.string(from: receipt.purchaseDate))
                if let payment = receipt.paymentMethod, !payment.isEmpty {
                    Text("• \(payment)")
                }
            }
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(labelText)
        .accessibilityHint("Opens receipt details")
    }
}

private extension ReceiptRowView {
    var labelText: String {
        var components: [String] = [receipt.merchantName]
        if let total = currencyFormatter.string(from: NSDecimalNumber(decimal: receipt.totalAmount)) {
            components.append("Total \(total)")
        }
        let date = dateFormatter.string(from: receipt.purchaseDate)
        components.append(date)
        if let payment = receipt.paymentMethod, !payment.isEmpty {
            components.append(payment)
        }
        return components.joined(separator: ", ")
    }
}

#Preview(traits: .sizeThatFitsLayout) {
    let sample = Receipt.previewSamples.first ?? Receipt(merchantName: "Preview", purchaseDate: Date(), totalAmount: 0)
    return ReceiptRowView(receipt: sample)
        .padding()
}
