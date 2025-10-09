//
//  ReceiptOcrService.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Foundation

struct ReceiptOcrCandidates {
    var merchantName: String?
    var purchaseDate: Date?
    var totalAmount: Decimal?
    var taxAmount: Decimal?
    var tipAmount: Decimal?
    var currencyCode: String?
}

struct ReceiptOcrResult {
    let rawText: String
    let localeIdentifier: String?
    let candidates: ReceiptOcrCandidates
}

protocol ReceiptOcrService {
    func recognizeText(in imageData: Data) async throws -> ReceiptOcrResult
}
