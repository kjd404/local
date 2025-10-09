import Foundation
import Testing
@testable import Plutary

@MainActor
struct PlutaryTests {

    @Test func manualReceiptFormValidationEnforcesRequiredFields() async throws {
        let store = StubReceiptStore()
        let viewModel = ManualReceiptFormViewModel(receiptStore: store)

        #expect(viewModel.saveEnabled == false)

        viewModel.merchantName = "Coffee Shop"
        viewModel.totalAmountText = "12.45"
        viewModel.formatCurrencyField(for: &viewModel.totalAmountText)

        #expect(viewModel.saveEnabled == true)

        viewModel.save()
        try await Task.sleep(nanoseconds: 50_000_000)

        #expect(store.createdDrafts.count == 1)
        #expect(store.createdDrafts.first?.merchantName == "Coffee Shop")
    }

    @Test func coreDataStorePersistsManualReceipt() async throws {
        let controller = await MainActor.run { PersistenceController(inMemory: true) }
        let store = controller.receiptStore
        var draft = ManualReceiptDraft()
        draft.merchantName = "Grocery Mart"
        draft.purchaseDate = Date(timeIntervalSince1970: 0)
        draft.totalAmount = Decimal(24.99)
        draft.taxAmount = Decimal(2.10)
        draft.paymentMethod = "Amex"

        let receipt = try await store.createManualReceipt(draft)
        #expect(receipt.merchantName == "Grocery Mart")
        #expect(receipt.totalAmount == Decimal(24.99))

        let receipts = try await store.fetchReceipts()
        #expect(receipts.count == 1)
    }

    @Test func ocrHeuristicsExtractKeyFields() {
        let service = VisionReceiptOcrService()
        let lines = [
            "Blue Bottle Coffee",
            "09/27/2025 08:41 AM",
            "Subtotal $12.35",
            "Tax $1.00",
            "Tip $2.00",
            "Total $15.35"
        ]

        let candidates = service.inferCandidates(from: lines)

        #expect(candidates.merchantName == "Blue Bottle Coffee")
        #expect(candidates.totalAmount == Decimal(string: "15.35"))
        #expect(candidates.taxAmount == Decimal(string: "1.00"))
        #expect(candidates.tipAmount == Decimal(string: "2.00"))
    }

    @Test func captureReviewPersistsReceipt() async throws {
        let storeController = PersistenceController(inMemory: true)
        let store = storeController.receiptStore
        let imageStore = InMemoryImageStore()

        let result = ReceiptOcrResult(
            rawText: "OCR Cafe\nTotal $18.99",
            localeIdentifier: "en_US",
            candidates: ReceiptOcrCandidates(
                merchantName: "OCR Cafe",
                purchaseDate: Date(timeIntervalSince1970: 1_724_352_000),
                totalAmount: Decimal(string: "18.99"),
                taxAmount: Decimal(string: "1.50"),
                tipAmount: Decimal(string: "2.50"),
                currencyCode: "USD"
            )
        )

        let viewModel = ReceiptCaptureReviewViewModel(
            imageData: Data(repeating: 0xA, count: 128),
            ocrResult: result,
            receiptStore: store,
            imageStore: imageStore
        )

        viewModel.save()
        try await Task.sleep(nanoseconds: 100_000_000)

        let receipts = try await store.fetchReceipts()
        #expect(receipts.count == 1)
        #expect(receipts.first?.merchantName == "OCR Cafe")
        #expect(imageStore.savedTokens.count == 1)
    }
}

private final class StubReceiptStore: ReceiptStoreProtocol {
    var createdDrafts: [ManualReceiptDraft] = []

    func fetchReceipts() async throws -> [Receipt] { [] }

    func observeReceipts() -> AsyncStream<[Receipt]> {
        AsyncStream { continuation in
            continuation.yield([])
        }
    }

    func receipt(id: UUID) async throws -> Receipt? { nil }

    @discardableResult
    func createManualReceipt(_ draft: ManualReceiptDraft) async throws -> Receipt {
        createdDrafts.append(draft)
        return Receipt(
            merchantName: draft.merchantName,
            purchaseDate: draft.purchaseDate,
            totalAmount: draft.totalAmount ?? .zero,
            taxAmount: draft.taxAmount,
            tipAmount: draft.tipAmount,
            paymentMethod: draft.normalizedPaymentMethod,
            notes: draft.normalizedNotes
        )
    }

    func upsert(_ receipt: Receipt) async throws {}

    func delete(id: UUID) async throws {}
}

private final class InMemoryImageStore: ReceiptImageStoreProtocol {
    private(set) var savedTokens: [String] = []
    private var storage: [String: Data] = [:]

    func storeImageData(_ data: Data, preferredToken: String?) throws -> String {
        let token = preferredToken ?? UUID().uuidString
        storage[token] = data
        savedTokens.append(token)
        return token
    }

    func imageData(for token: String) throws -> Data {
        guard let data = storage[token] else {
            throw ReceiptImageStoreError.readFailed
        }
        return data
    }

    func deleteImage(for token: String) throws {
        storage.removeValue(forKey: token)
    }
}
