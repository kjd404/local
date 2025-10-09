//
//  Receipt.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Foundation

enum ReceiptAutoFillField: String, CaseIterable, Hashable {
    case merchantName
    case purchaseDate
    case totalAmount
    case taxAmount
    case tipAmount
    case paymentMethod
}

struct Receipt: Identifiable, Hashable {
    let id: UUID
    var merchantName: String
    var purchaseDate: Date
    var totalAmount: Decimal
    var taxAmount: Decimal?
    var tipAmount: Decimal?
    var paymentMethod: String?
    var notes: String?
    var imageToken: String?
    var ocrText: String?
    var ocrLocaleIdentifier: String?
    var createdAt: Date
    var updatedAt: Date
    var lineItems: [ReceiptLineItem]
    var autoFilledFields: Set<ReceiptAutoFillField>

    init(
        id: UUID = UUID(),
        merchantName: String,
        purchaseDate: Date,
        totalAmount: Decimal,
        taxAmount: Decimal? = nil,
        tipAmount: Decimal? = nil,
        paymentMethod: String? = nil,
        notes: String? = nil,
        imageToken: String? = nil,
        ocrText: String? = nil,
        ocrLocaleIdentifier: String? = nil,
        createdAt: Date = Date(),
        updatedAt: Date = Date(),
        lineItems: [ReceiptLineItem] = [],
        autoFilledFields: Set<ReceiptAutoFillField> = []
    ) {
        self.id = id
        self.merchantName = merchantName
        self.purchaseDate = purchaseDate
        self.totalAmount = totalAmount
        self.taxAmount = taxAmount
        self.tipAmount = tipAmount
        self.paymentMethod = paymentMethod
        self.notes = notes
        self.imageToken = imageToken
        self.ocrText = ocrText
        self.ocrLocaleIdentifier = ocrLocaleIdentifier
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.lineItems = lineItems
        self.autoFilledFields = autoFilledFields
    }

    var hasImage: Bool {
        imageToken != nil
    }

    var hasOcrPayload: Bool {
        !(ocrText?.isEmpty ?? true)
    }
}

struct ReceiptLineItem: Identifiable, Hashable {
    let id: UUID
    var description: String
    var quantity: Decimal?
    var unitPrice: Decimal?
    var total: Decimal?

    init(
        id: UUID = UUID(),
        description: String,
        quantity: Decimal? = nil,
        unitPrice: Decimal? = nil,
        total: Decimal? = nil
    ) {
        self.id = id
        self.description = description
        self.quantity = quantity
        self.unitPrice = unitPrice
        self.total = total
    }
}

extension ReceiptAutoFillField {
    var displayName: String {
        switch self {
        case .merchantName: return "Merchant"
        case .purchaseDate: return "Date"
        case .totalAmount: return "Total"
        case .taxAmount: return "Tax"
        case .tipAmount: return "Tip"
        case .paymentMethod: return "Payment"
        }
    }
}

#if DEBUG
extension Receipt {
    static let previewSamples: [Receipt] = [
        Receipt(
            merchantName: "Blue Bottle Coffee",
            purchaseDate: Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date(),
            totalAmount: Decimal(string: "14.35") ?? 14.35,
            taxAmount: Decimal(string: "1.20"),
            tipAmount: Decimal(string: "2.00"),
            paymentMethod: "Visa •••• 4242",
            notes: "Latte run with Alex",
            imageToken: "sample-coffee.jpg",
            ocrText: "Blue Bottle Coffee\n09/27/2025 08:41 AM\nLatte 5.00\nCroissant 4.50\nTotal 14.35",
            ocrLocaleIdentifier: "en_US",
            createdAt: Date().addingTimeInterval(-3600),
            updatedAt: Date().addingTimeInterval(-3600),
            lineItems: [
                ReceiptLineItem(description: "Latte", quantity: Decimal(1), unitPrice: Decimal(string: "5.00"), total: Decimal(string: "5.00")),
                ReceiptLineItem(description: "Croissant", quantity: Decimal(1), unitPrice: Decimal(string: "4.50"), total: Decimal(string: "4.50"))
            ],
            autoFilledFields: [.merchantName, .totalAmount, .purchaseDate]
        ),
        Receipt(
            merchantName: "City Grocery",
            purchaseDate: Calendar.current.date(byAdding: .day, value: -3, to: Date()) ?? Date(),
            totalAmount: Decimal(string: "82.19") ?? 82.19,
            taxAmount: Decimal(string: "6.79"),
            tipAmount: nil,
            paymentMethod: "Apple Pay",
            notes: nil,
            imageToken: nil,
            ocrText: "City Grocery\n09/25/2025\nProduce 25.11\nPantry 35.00\nTotal 82.19",
            ocrLocaleIdentifier: "en_US",
            createdAt: Date().addingTimeInterval(-86_400),
            updatedAt: Date().addingTimeInterval(-86_400),
            lineItems: [
                ReceiptLineItem(description: "Honeycrisp Apples", quantity: Decimal(4), unitPrice: Decimal(string: "1.99"), total: Decimal(string: "7.96")),
                ReceiptLineItem(description: "Granola", quantity: Decimal(2), unitPrice: Decimal(string: "5.50"), total: Decimal(string: "11.00"))
            ],
            autoFilledFields: [.totalAmount]
        )
    ]
}
#endif
