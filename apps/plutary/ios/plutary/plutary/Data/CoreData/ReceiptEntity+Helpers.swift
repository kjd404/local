import Foundation

extension ReceiptEntity {
    var lineItemsArray: [LineItemEntity] {
        (lineItems?.allObjects as? [LineItemEntity]) ?? []
    }
}
