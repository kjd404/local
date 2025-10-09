//
//  CoreDataReceiptStore.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import CoreData
import Foundation
import OSLog

enum ReceiptStoreError: Error {
    case missingEntity
    case persistenceFailure(underlying: Error)
    case validationFailed(String)
}

final class CoreDataReceiptStore: ReceiptStoreProtocol {
    private let container: NSPersistentContainer
    private let viewContext: NSManagedObjectContext
    private let backgroundContext: NSManagedObjectContext
    private let notificationCenter: NotificationCenter

    init(container: NSPersistentContainer, notificationCenter: NotificationCenter = .default) {
        self.container = container
        self.viewContext = container.viewContext
        self.backgroundContext = container.newBackgroundContext()
        self.backgroundContext.mergePolicy = NSMergeByPropertyObjectTrumpMergePolicy
        self.notificationCenter = notificationCenter
    }

    func fetchReceipts() async throws -> [Receipt] {
        try await viewContext.perform {
            let request = ReceiptEntity.fetchRequest()
            request.sortDescriptors = [NSSortDescriptor(key: #keyPath(ReceiptEntity.purchaseDate), ascending: false)]
            do {
                let entities = try self.viewContext.fetch(request)
                return entities.map(self.map(entity:))
            } catch {
                throw ReceiptStoreError.persistenceFailure(underlying: error)
            }
        }
    }

    func observeReceipts() -> AsyncStream<[Receipt]> {
        AsyncStream { continuation in
            let token = notificationCenter.addObserver(
                forName: .NSManagedObjectContextDidSave,
                object: nil,
                queue: nil
            ) { [weak self] _ in
                guard let self else { return }
                Task {
                    if let receipts = try? await self.fetchReceipts() {
                        continuation.yield(receipts)
                    }
                }
            }

            Task {
                if let receipts = try? await self.fetchReceipts() {
                    continuation.yield(receipts)
                }
            }

            continuation.onTermination = { [notificationCenter] _ in
                notificationCenter.removeObserver(token)
            }
        }
    }

    func receipt(id: UUID) async throws -> Receipt? {
        try await viewContext.perform {
            let request = ReceiptEntity.fetchRequest()
            request.predicate = NSPredicate(format: "id == %@", id as CVarArg)
            request.fetchLimit = 1
            let entities = try self.viewContext.fetch(request)
            return entities.first.map(self.map(entity:))
        }
    }

    func createManualReceipt(_ draft: ManualReceiptDraft) async throws -> Receipt {
        try await backgroundContext.perform { [self] in
            guard let total = draft.totalAmount else {
                throw ReceiptStoreError.validationFailed("Total amount must be provided before saving a manual receipt.")
            }

            let entity = ReceiptEntity(context: self.backgroundContext)
            let now = Date()
            entity.id = UUID()
            let merchant = draft.merchantName.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !merchant.isEmpty else {
                throw ReceiptStoreError.validationFailed("Merchant name cannot be empty.")
            }

            entity.merchantName = merchant
            entity.purchaseDate = draft.purchaseDate
            entity.totalAmount = NSDecimalNumber(decimal: total)
            entity.taxAmount = draft.taxAmount.map { NSDecimalNumber(decimal: $0) }
            entity.tipAmount = draft.tipAmount.map { NSDecimalNumber(decimal: $0) }
            entity.paymentMethod = draft.normalizedPaymentMethod
            entity.notes = draft.normalizedNotes
            entity.createdAt = now
            entity.updatedAt = now
            entity.autoFilledFields = nil

            for item in draft.lineItems {
                let line = LineItemEntity(context: self.backgroundContext)
                line.id = item.id
                line.descriptionText = item.description
                line.quantity = item.quantity.map { NSDecimalNumber(decimal: $0) }
                line.unitPrice = item.unitPrice.map { NSDecimalNumber(decimal: $0) }
                line.total = item.total.map { NSDecimalNumber(decimal: $0) }
                line.receipt = entity
            }

            do {
                try self.backgroundContext.save()
            } catch {
                self.backgroundContext.reset()
                AppLogger.receipts.error("Failed to save manual receipt: \(error.localizedDescription, privacy: .public)")
                throw ReceiptStoreError.persistenceFailure(underlying: error)
            }

            return self.map(entity: entity)
        }
    }

    func upsert(_ receipt: Receipt) async throws {
        try await backgroundContext.perform {
            let fetchRequest = ReceiptEntity.fetchRequest()
            fetchRequest.predicate = NSPredicate(format: "id == %@", receipt.id as CVarArg)
            fetchRequest.fetchLimit = 1

            let targetEntity: ReceiptEntity
            if let existing = try self.backgroundContext.fetch(fetchRequest).first {
                targetEntity = existing
            } else {
                targetEntity = ReceiptEntity(context: self.backgroundContext)
                targetEntity.id = receipt.id
                targetEntity.createdAt = receipt.createdAt
            }

            let merchant = receipt.merchantName.trimmingCharacters(in: .whitespacesAndNewlines)
            targetEntity.merchantName = merchant
            targetEntity.purchaseDate = receipt.purchaseDate
            targetEntity.totalAmount = NSDecimalNumber(decimal: receipt.totalAmount)
            targetEntity.taxAmount = receipt.taxAmount.map { NSDecimalNumber(decimal: $0) }
            targetEntity.tipAmount = receipt.tipAmount.map { NSDecimalNumber(decimal: $0) }
            targetEntity.paymentMethod = receipt.paymentMethod
            targetEntity.notes = receipt.notes
            targetEntity.imageToken = receipt.imageToken
            targetEntity.ocrText = receipt.ocrText
            targetEntity.ocrLocale = receipt.ocrLocaleIdentifier
            targetEntity.updatedAt = receipt.updatedAt
            targetEntity.autoFilledFields = self.encodeAutoFilledFields(receipt.autoFilledFields)

            // Replace line items wholesale for now.
            if let existing = targetEntity.lineItems {
                for case let line as LineItemEntity in existing {
                    self.backgroundContext.delete(line)
                }
            }

            for lineItem in receipt.lineItems {
                let line = LineItemEntity(context: self.backgroundContext)
                line.id = lineItem.id
                line.descriptionText = lineItem.description
                line.quantity = lineItem.quantity.map { NSDecimalNumber(decimal: $0) }
                line.unitPrice = lineItem.unitPrice.map { NSDecimalNumber(decimal: $0) }
                line.total = lineItem.total.map { NSDecimalNumber(decimal: $0) }
                line.receipt = targetEntity
            }

            do {
                try self.backgroundContext.save()
            } catch {
                self.backgroundContext.reset()
                AppLogger.receipts.error("Failed to upsert receipt: \(error.localizedDescription, privacy: .public)")
                throw ReceiptStoreError.persistenceFailure(underlying: error)
            }
        }
    }

    func delete(id: UUID) async throws {
        try await backgroundContext.perform {
            let fetchRequest = ReceiptEntity.fetchRequest()
            fetchRequest.predicate = NSPredicate(format: "id == %@", id as CVarArg)
            fetchRequest.fetchLimit = 1
            if let entity = try self.backgroundContext.fetch(fetchRequest).first {
                self.backgroundContext.delete(entity)
                do {
                    try self.backgroundContext.save()
                } catch {
                    self.backgroundContext.reset()
                    AppLogger.receipts.error("Failed to delete receipt: \(error.localizedDescription, privacy: .public)")
                    throw ReceiptStoreError.persistenceFailure(underlying: error)
                }
            }
        }
    }

    // MARK: - Mapping

    private func map(entity: ReceiptEntity) -> Receipt {
        Receipt(
            id: entity.id ?? UUID(),
            merchantName: entity.merchantName ?? "",
            purchaseDate: entity.purchaseDate ?? Date(),
            totalAmount: entity.totalAmount?.decimalValue ?? .zero,
            taxAmount: entity.taxAmount?.decimalValue,
            tipAmount: entity.tipAmount?.decimalValue,
            paymentMethod: entity.paymentMethod,
            notes: entity.notes,
            imageToken: entity.imageToken,
            ocrText: entity.ocrText,
            ocrLocaleIdentifier: entity.ocrLocale,
            createdAt: entity.createdAt ?? Date(),
            updatedAt: entity.updatedAt ?? Date(),
            lineItems: entity.lineItemsArray.map(map(lineItem:)),
            autoFilledFields: decodeAutoFilledFields(entity.autoFilledFields)
        )
    }

    private func map(lineItem: LineItemEntity) -> ReceiptLineItem {
        ReceiptLineItem(
            id: lineItem.id ?? UUID(),
            description: lineItem.descriptionText ?? "",
            quantity: lineItem.quantity?.decimalValue,
            unitPrice: lineItem.unitPrice?.decimalValue,
            total: lineItem.total?.decimalValue
        )
    }
}

// MARK: - Auto-filled mapping helpers

private extension CoreDataReceiptStore {
    func encodeAutoFilledFields(_ fields: Set<ReceiptAutoFillField>) -> String? {
        guard !fields.isEmpty else { return nil }
        return fields.map { $0.rawValue }.sorted().joined(separator: ",")
    }

    func decodeAutoFilledFields(_ rawValue: String?) -> Set<ReceiptAutoFillField> {
        guard let rawValue, !rawValue.isEmpty else { return [] }
        let components = rawValue.split(separator: ",").map(String.init)
        return Set(components.compactMap(ReceiptAutoFillField.init(rawValue:)))
    }
}
