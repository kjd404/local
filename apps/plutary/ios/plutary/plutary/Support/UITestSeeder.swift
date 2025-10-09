//
//  UITestSeeder.swift
//  Plutary
//
//  Created by Codex on 10/8/25.
//

import Foundation
import OSLog

@MainActor
enum UITestSeeder {
    static func seedIfNeeded(using store: ReceiptStoreProtocol, arguments: [String]) async {
        guard arguments.contains("--uitests") else { return }

        let fixture = fixtureIdentifier(from: arguments)
        await replaceStoreContents(using: store, with: fixture.receipts)
    }

    private static func fixtureIdentifier(from arguments: [String]) -> Fixture {
        guard let flagIndex = arguments.firstIndex(of: "--fixture"),
              arguments.indices.contains(flagIndex + 1),
              let candidate = Fixture(rawValue: arguments[flagIndex + 1])
        else {
            return .sample
        }
        return candidate
    }

    private static func replaceStoreContents(using store: ReceiptStoreProtocol, with receipts: [Receipt]) async {
        do {
            let existing = try await store.fetchReceipts()
            for receipt in existing {
                try? await store.delete(id: receipt.id)
            }
            for receipt in receipts {
                try await store.upsert(receipt)
            }
        } catch {
            AppLogger.receipts.error("Failed to seed UI test fixtures: \(error.localizedDescription, privacy: .public)")
        }
    }

    enum Fixture: String {
        case empty
        case sample

        var receipts: [Receipt] {
            switch self {
            case .empty:
                return []
            case .sample:
                return [
                    Receipt(
                        merchantName: "Sample Deli",
                        purchaseDate: ISO8601DateFormatter().date(from: "2024-09-17T18:05:00Z") ?? Date(),
                        totalAmount: Decimal(string: "21.45") ?? 21.45,
                        taxAmount: Decimal(string: "1.85"),
                        tipAmount: Decimal(string: "3.00"),
                        paymentMethod: "Visa •••• 1111",
                        notes: "Seeded receipt",
                        imageToken: nil,
                        ocrText: "Sample Deli\n09/17/2024\nTotal $21.45",
                        ocrLocaleIdentifier: "en_US",
                        createdAt: Date(timeIntervalSince1970: 1_724_603_900),
                        updatedAt: Date(timeIntervalSince1970: 1_724_603_900),
                        lineItems: [
                            ReceiptLineItem(description: "Turkey Sandwich", quantity: Decimal(1), unitPrice: Decimal(string: "9.95"), total: Decimal(string: "9.95")),
                            ReceiptLineItem(description: "Iced Tea", quantity: Decimal(1), unitPrice: Decimal(string: "3.50"), total: Decimal(string: "3.50"))
                        ],
                        autoFilledFields: [.merchantName, .purchaseDate, .totalAmount]
                    ),
                    Receipt(
                        merchantName: "Blue Bottle Coffee",
                        purchaseDate: ISO8601DateFormatter().date(from: "2024-09-15T08:12:00Z") ?? Date(),
                        totalAmount: Decimal(string: "14.35") ?? 14.35,
                        taxAmount: Decimal(string: "1.20"),
                        tipAmount: Decimal(string: "2.00"),
                        paymentMethod: "Apple Pay",
                        notes: nil,
                        imageToken: nil,
                        ocrText: "Blue Bottle Coffee\nLatte 5.00\nTotal 14.35",
                        ocrLocaleIdentifier: "en_US",
                        createdAt: Date(timeIntervalSince1970: 1_724_439_920),
                        updatedAt: Date(timeIntervalSince1970: 1_724_439_920),
                        lineItems: [
                            ReceiptLineItem(description: "Latte", quantity: Decimal(1), unitPrice: Decimal(string: "5.00"), total: Decimal(string: "5.00")),
                            ReceiptLineItem(description: "Croissant", quantity: Decimal(1), unitPrice: Decimal(string: "4.50"), total: Decimal(string: "4.50"))
                        ],
                        autoFilledFields: [.merchantName, .totalAmount, .purchaseDate]
                    )
                ]
            }
        }
    }
}
