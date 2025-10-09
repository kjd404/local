//
//  ReceiptsListViewModel.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Combine
import Foundation
import OSLog
import SwiftUI

@MainActor
final class ReceiptsListViewModel: ObservableObject {
    struct AlertState: Identifiable {
        let id = UUID()
        let message: String
    }

    @Published private(set) var receipts: [Receipt] = []
    @Published var alert: AlertState?

    private let receiptStore: ReceiptStoreProtocol
    private var observationTask: Task<Void, Never>?

    init(receiptStore: ReceiptStoreProtocol) {
        self.receiptStore = receiptStore
    }

    func onAppear() {
        guard observationTask == nil else { return }
        observationTask = Task { [weak self] in
            guard let self else { return }
            for await receipts in self.receiptStore.observeReceipts() {
                await MainActor.run {
                    self.receipts = receipts.sorted(by: { $0.purchaseDate > $1.purchaseDate })
                }
            }
        }
    }

    func onDisappear() {
        observationTask?.cancel()
        observationTask = nil
    }

    func removeReceipts(at offsets: IndexSet) {
        let ids = offsets.compactMap { index in
            receipts[safe: index]?.id
        }
        Task {
            for id in ids {
                do {
                    try await receiptStore.delete(id: id)
                } catch {
                    AppLogger.receipts.error("Failed to delete receipt: \(error.localizedDescription, privacy: .public)")
                    await MainActor.run {
                        self.alert = AlertState(message: "Failed to delete receipt: \(error.localizedDescription)")
                    }
                }
            }
        }
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
