//
//  ReceiptDetailView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct ReceiptDetailView: View {
    @StateObject var viewModel: ReceiptDetailViewModel
    let imageStore: ReceiptImageStoreProtocol

    @State private var receiptImage: Image?

    private let currencyFormatter: NumberFormatter

    init(viewModel: ReceiptDetailViewModel, imageStore: ReceiptImageStoreProtocol, locale: Locale = .current) {
        _viewModel = StateObject(wrappedValue: viewModel)
        self.imageStore = imageStore
        currencyFormatter = NumberFormatter()
        currencyFormatter.numberStyle = .currency
        currencyFormatter.generatesDecimalNumbers = true
        currencyFormatter.locale = locale
    }

    var body: some View {
        List {
            if let receiptImage {
                Section {
                    receiptImage
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .shadow(radius: 4)
                        .padding(.vertical, 8)
                }
            } else if viewModel.receipt.hasImage {
                Section {
                    ProgressView("Loading image…")
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }

            Section("Summary") {
                LabeledContent("Merchant", value: viewModel.merchantName)
                LabeledContent("Date", value: viewModel.formattedPurchaseDate)
                LabeledContent("Total", value: viewModel.formattedTotal)
                if let tax = viewModel.formattedTax {
                    LabeledContent("Tax", value: tax)
                }
                if let tip = viewModel.formattedTip {
                    LabeledContent("Tip", value: tip)
                }
                if let payment = viewModel.receipt.paymentMethod, !payment.isEmpty {
                    LabeledContent("Payment", value: payment)
                }
                if let autoFilledSummary {
                    LabeledContent("Auto-filled", value: autoFilledSummary)
                }
            }

            if !viewModel.receipt.lineItems.isEmpty {
                Section("Line Items") {
                    ForEach(viewModel.receipt.lineItems) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.description)
                                .font(.body)
                            HStack {
                                if let quantity = item.quantity {
                                    Text("Qty \(quantity.formatted())")
                                }
                                if let unitPrice = item.unitPrice {
                                    Text("@ \(currencyFormatter.string(from: NSDecimalNumber(decimal: unitPrice)) ?? "")")
                                }
                                Spacer()
                                if let total = item.total {
                                    Text(currencyFormatter.string(from: NSDecimalNumber(decimal: total)) ?? "")
                                        .font(.headline)
                                }
                            }
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            if let notes = viewModel.receipt.notes, !notes.isEmpty {
                Section("Notes") {
                    Text(notes)
                }
            }

            if viewModel.receipt.hasOcrPayload, let text = viewModel.receipt.ocrText {
                Section("Recognized Text") {
                    Text(text)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
            }
        }
        .navigationTitle(viewModel.merchantName)
        .toolbarTitleDisplayMode(.inline)
        .task {
            await loadImageIfNeeded()
        }
    }

    private var autoFilledSummary: String? {
        let fields = viewModel.receipt.autoFilledFields
        guard !fields.isEmpty else { return nil }
        return fields
            .sorted { $0.displayName < $1.displayName }
            .map { $0.displayName }
            .joined(separator: ", ")
    }

    private func loadImageIfNeeded() async {
        guard receiptImage == nil, let token = viewModel.receipt.imageToken else { return }
        do {
            let data = try imageStore.imageData(for: token)
            #if canImport(UIKit)
            if let uiImage = UIImage(data: data) {
                await MainActor.run {
                    receiptImage = Image(uiImage: uiImage)
                }
            }
            #endif
        } catch {
            // Intentionally swallow; we surface missing image as placeholder.
        }
    }
}

#Preview {
    let controller = PersistenceController.preview
    return NavigationStack {
        ReceiptDetailView(
            viewModel: ReceiptDetailViewModel(receipt: Receipt.previewSamples.first!),
            imageStore: controller.imageStore
        )
    }
}
