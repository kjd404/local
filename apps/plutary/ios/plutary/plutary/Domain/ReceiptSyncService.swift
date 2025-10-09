import Foundation

protocol ReceiptSyncService {
    func queueForUpload(_ receipt: Receipt) async
    func markSynced(id: UUID) async
}

struct LocalReceiptSyncService: ReceiptSyncService {
    func queueForUpload(_ receipt: Receipt) async {}
    func markSynced(id: UUID) async {}
}
