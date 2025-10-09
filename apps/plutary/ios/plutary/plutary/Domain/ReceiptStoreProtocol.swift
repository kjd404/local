//
//  ReceiptStoreProtocol.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Foundation

struct ManualReceiptDraft: Equatable {
    var merchantName: String = ""
    var purchaseDate: Date = Date()
    var totalAmount: Decimal?
    var taxAmount: Decimal?
    var tipAmount: Decimal?
    var paymentMethod: String = ""
    var notes: String = ""
    var lineItems: [ReceiptLineItem] = []

    var normalizedPaymentMethod: String? {
        paymentMethod.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : paymentMethod
    }

    var normalizedNotes: String? {
        notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes
    }

    var isValid: Bool {
        totalAmount != nil && !merchantName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

protocol ReceiptStoreProtocol {
    func fetchReceipts() async throws -> [Receipt]
    func observeReceipts() -> AsyncStream<[Receipt]>
    func receipt(id: UUID) async throws -> Receipt?
    @discardableResult
    func createManualReceipt(_ draft: ManualReceiptDraft) async throws -> Receipt
    func upsert(_ receipt: Receipt) async throws
    func delete(id: UUID) async throws
}

protocol ReceiptImageStoreProtocol {
    func storeImageData(_ data: Data, preferredToken: String?) throws -> String
    func imageData(for token: String) throws -> Data
    func deleteImage(for token: String) throws
}
