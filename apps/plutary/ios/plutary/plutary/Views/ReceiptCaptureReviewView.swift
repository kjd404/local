//
//  ReceiptCaptureReviewView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct ReceiptCaptureReviewView: View {
    @ObservedObject var viewModel: ReceiptCaptureReviewViewModel
    @FocusState private var focusedField: Field?
    @State private var capturedImage: Image?

    enum Field: Hashable {
        case merchant
        case total
        case tax
        case tip
        case payment
    }

    var body: some View {
        Form {
            if let capturedImage {
                Section {
                    capturedImage
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .shadow(radius: 4)
                        .padding(.vertical, 8)
                }
            }

            Section("Details") {
                TextField("Merchant", text: $viewModel.merchantName)
                    .focused($focusedField, equals: .merchant)
                    .if(viewModel.autoFilledFields.contains(.merchantName)) { view in
                        view.overlay(alignment: .trailing) {
                            AutoFillBadge()
                        }
                    }
                DatePicker("Purchase Date", selection: $viewModel.purchaseDate, displayedComponents: .date)
                TextField("Total", text: $viewModel.totalAmountText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .total)
                    .if(viewModel.autoFilledFields.contains(.totalAmount)) { view in
                        view.overlay(alignment: .trailing) { AutoFillBadge() }
                    }
                TextField("Tax", text: $viewModel.taxAmountText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .tax)
                    .if(viewModel.autoFilledFields.contains(.taxAmount)) { view in
                        view.overlay(alignment: .trailing) { AutoFillBadge() }
                    }
                TextField("Tip", text: $viewModel.tipAmountText)
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .tip)
                    .if(viewModel.autoFilledFields.contains(.tipAmount)) { view in
                        view.overlay(alignment: .trailing) { AutoFillBadge() }
                    }
                TextField("Payment Method", text: $viewModel.paymentMethod)
                    .focused($focusedField, equals: .payment)
            }

            Section("Notes") {
                TextEditor(text: $viewModel.notes)
                    .frame(minHeight: 120)
            }

            Section("Recognized Text") {
                Text(viewModel.recognizedText.isEmpty ? "No text detected" : viewModel.recognizedText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
        }
        .navigationTitle("Review Receipt")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    viewModel.normalizeCurrencyFields()
                    viewModel.save()
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save")
                    }
                }
                .disabled(!viewModel.saveEnabled || viewModel.isSaving)
            }
        }
        .alert("Unable to Save", isPresented: Binding(
            get: { viewModel.alertMessage != nil },
            set: { _ in viewModel.alertMessage = nil }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.alertMessage?.message ?? "")
        }
        .onAppear(perform: loadImage)
    }

    private func loadImage() {
        #if canImport(UIKit)
        if let uiImage = UIImage(data: viewModel.imageData) {
            capturedImage = Image(uiImage: uiImage)
        }
        #endif
    }
}

private struct AutoFillBadge: View {
    var body: some View {
        Text("Auto-filled")
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(.green.opacity(0.2))
            .clipShape(Capsule())
            .padding(.trailing, 4)
    }
}

private extension View {
    @ViewBuilder
    func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
}

#Preview {
    let controller = PersistenceController.preview
    let ocrResult = ReceiptOcrResult(
        rawText: "Coffee Shop\nTotal $12.45",
        localeIdentifier: "en_US",
        candidates: ReceiptOcrCandidates(
            merchantName: "Coffee Shop",
            purchaseDate: Date(),
            totalAmount: Decimal(12.45),
            taxAmount: Decimal(1.02),
            tipAmount: Decimal(2.00),
            currencyCode: "USD"
        )
    )
    let vm = ReceiptCaptureReviewViewModel(
        imageData: Data(),
        ocrResult: ocrResult,
        receiptStore: controller.receiptStore,
        imageStore: controller.imageStore
    )
    return NavigationStack {
        ReceiptCaptureReviewView(viewModel: vm)
    }
}
