//
//  ReceiptCaptureViewModel.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Combine
import Foundation
import OSLog
import SwiftUI

@MainActor
final class ReceiptCaptureViewModel: ObservableObject, Identifiable {
    let id = UUID()
    @Published var isProcessing = false
    @Published var reviewViewModel: ReceiptCaptureReviewViewModel?
    @Published var alert: ReceiptsListViewModel.AlertState?

    var onComplete: (() -> Void)?

    private let receiptStore: ReceiptStoreProtocol
    private let imageStore: ReceiptImageStoreProtocol
    private let ocrService: ReceiptOcrService

    init(
        receiptStore: ReceiptStoreProtocol,
        imageStore: ReceiptImageStoreProtocol,
        ocrService: ReceiptOcrService
    ) {
        self.receiptStore = receiptStore
        self.imageStore = imageStore
        self.ocrService = ocrService
    }

    func reset() {
        reviewViewModel = nil
        isProcessing = false
        alert = nil
    }

    func processImageData(_ data: Data) {
        isProcessing = true
        Task {
            do {
                let result = try await ocrService.recognizeText(in: data)
                await MainActor.run {
                    let review = ReceiptCaptureReviewViewModel(
                        imageData: data,
                        ocrResult: result,
                        receiptStore: receiptStore,
                        imageStore: imageStore
                    )
                    review.onComplete = { [weak self] in
                        self?.onComplete?()
                        self?.reset()
                    }
                    self.reviewViewModel = review
                    self.isProcessing = false
                }
            } catch {
                AppLogger.ocr.error("OCR processing failed: \(error.localizedDescription, privacy: .public)")
                await MainActor.run {
                    self.alert = ReceiptsListViewModel.AlertState(message: "We couldn't process that image. \(error.localizedDescription)")
                    self.isProcessing = false
                }
            }
        }
    }
}
