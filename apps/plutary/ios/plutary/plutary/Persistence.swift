//
//  Persistence.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import CoreData
import Foundation

@MainActor
final class PersistenceController {
    static let shared = PersistenceController()

    static let preview: PersistenceController = {
        let controller = PersistenceController(inMemory: true)
        controller.seedPreviewData()
        return controller
    }()

    let container: NSPersistentContainer
    let receiptStore: ReceiptStoreProtocol
    let imageStore: ReceiptImageStoreProtocol

    init(inMemory: Bool = false) {
        container = NSPersistentContainer(name: "Plutary")
        if inMemory, let description = container.persistentStoreDescriptions.first {
            description.url = URL(fileURLWithPath: "/dev/null")
        }

        container.loadPersistentStores { _, error in
            if let error {
                fatalError("Unresolved Core Data error: \(error)")
            }
        }

        container.viewContext.automaticallyMergesChangesFromParent = true
        container.viewContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy

        receiptStore = CoreDataReceiptStore(container: container)
        imageStore = (try? FileReceiptImageStore()) ?? FailoverImageStore()
    }

    private func seedPreviewData() {
        #if DEBUG
        let context = container.viewContext
        for sample in Receipt.previewSamples {
            let entity = ReceiptEntity(context: context)
            entity.id = sample.id
            entity.merchantName = sample.merchantName
            entity.purchaseDate = sample.purchaseDate
            entity.totalAmount = NSDecimalNumber(decimal: sample.totalAmount)
            entity.taxAmount = sample.taxAmount.map { NSDecimalNumber(decimal: $0) }
            entity.tipAmount = sample.tipAmount.map { NSDecimalNumber(decimal: $0) }
            entity.paymentMethod = sample.paymentMethod
            entity.notes = sample.notes
            entity.imageToken = sample.imageToken
            entity.ocrText = sample.ocrText
            entity.ocrLocale = sample.ocrLocaleIdentifier
            entity.createdAt = sample.createdAt
            entity.updatedAt = sample.updatedAt
            entity.autoFilledFields = sample.autoFilledFields.isEmpty ? nil : sample.autoFilledFields.map { $0.rawValue }.sorted().joined(separator: ",")

            sample.lineItems.forEach { line in
                let lineEntity = LineItemEntity(context: context)
                lineEntity.id = line.id
                lineEntity.descriptionText = line.description
                lineEntity.quantity = line.quantity.map { NSDecimalNumber(decimal: $0) }
                lineEntity.unitPrice = line.unitPrice.map { NSDecimalNumber(decimal: $0) }
                lineEntity.total = line.total.map { NSDecimalNumber(decimal: $0) }
                lineEntity.receipt = entity
            }
        }

        do {
            try context.save()
        } catch {
            assertionFailure("Failed to seed preview data: \(error)")
        }
        #endif
    }
}

private final class FailoverImageStore: ReceiptImageStoreProtocol {
    func storeImageData(_ data: Data, preferredToken: String?) throws -> String {
        preferredToken ?? UUID().uuidString
    }

    func imageData(for token: String) throws -> Data {
        Data()
    }

    func deleteImage(for token: String) throws {}
}
