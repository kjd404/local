//
//  ManualReceiptFormView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import SwiftUI

struct ManualReceiptFormView: View {
    @ObservedObject var viewModel: ManualReceiptFormViewModel
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focusedField: Field?

    enum Field: Hashable {
        case total
        case tax
        case tip
        case lineItemQuantity(UUID)
        case lineItemUnit(UUID)
        case lineItemTotal(UUID)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Merchant") {
                    TextField("Merchant Name", text: $viewModel.merchantName)
                        .textInputAutocapitalization(.words)
                }

                Section("Purchase Details") {
                    DatePicker("Purchase Date", selection: $viewModel.purchaseDate, displayedComponents: .date)
                    TextField("Total Amount", text: $viewModel.totalAmountText)
                        .keyboardType(.decimalPad)
                        .focused($focusedField, equals: .total)
                        .onSubmit { viewModel.formatCurrencyField(for: &viewModel.totalAmountText) }
                    TextField("Tax (optional)", text: $viewModel.taxAmountText)
                        .keyboardType(.decimalPad)
                        .focused($focusedField, equals: .tax)
                        .onSubmit { viewModel.formatCurrencyField(for: &viewModel.taxAmountText) }
                    TextField("Tip (optional)", text: $viewModel.tipAmountText)
                        .keyboardType(.decimalPad)
                        .focused($focusedField, equals: .tip)
                        .onSubmit { viewModel.formatCurrencyField(for: &viewModel.tipAmountText) }
                    TextField("Payment Method", text: $viewModel.paymentMethod)
                }

                Section("Notes") {
                    TextEditor(text: $viewModel.notes)
                        .frame(minHeight: 120)
                        .accessibilityLabel("Notes")
                }

                Section("Line Items") {
                    if viewModel.lineItems.isEmpty {
                        Text("Add items to break down the purchase.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach($viewModel.lineItems) { $lineItem in
                        VStack(alignment: .leading, spacing: 8) {
                            TextField("Description", text: $lineItem.description)
                            HStack {
                                TextField("Qty", text: $lineItem.quantityText)
                                    .keyboardType(.decimalPad)
                                    .focused($focusedField, equals: .lineItemQuantity(lineItem.id))
                                TextField("Unit Price", text: $lineItem.unitPriceText)
                                    .keyboardType(.decimalPad)
                                    .focused($focusedField, equals: .lineItemUnit(lineItem.id))
                                TextField("Line Total", text: $lineItem.totalText)
                                    .keyboardType(.decimalPad)
                                    .focused($focusedField, equals: .lineItemTotal(lineItem.id))
                            }
                        }
                        .onSubmit {
                            viewModel.formatCurrencyField(for: &lineItem.unitPriceText)
                            viewModel.formatCurrencyField(for: &lineItem.totalText)
                        }
                    }
                    .onDelete(perform: viewModel.removeLineItems)

                    Button {
                        viewModel.addLineItem()
                    } label: {
                        Label("Add Line Item", systemImage: "plus.circle")
                    }
                }
            }
            .navigationTitle("New Receipt")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", role: .cancel) {
                        dismiss()
                    }
                }
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
            .onAppear {
                let existingHandler = viewModel.onSaveSuccess
                viewModel.onSaveSuccess = {
                    existingHandler?()
                    dismiss()
                }
            }
            .onDisappear {
                viewModel.onSaveSuccess = nil
            }
            .alert("Unable to Save", isPresented: Binding(
                get: { viewModel.alertMessage != nil },
                set: { _ in viewModel.alertMessage = nil }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.alertMessage?.message ?? "")
            }
        }
    }
}

#Preview {
    ManualReceiptFormView(
        viewModel: {
            let controller = PersistenceController.preview
            let vm = ManualReceiptFormViewModel(receiptStore: controller.receiptStore)
            vm.merchantName = "Preview Merchant"
            vm.totalAmountText = "12.45"
            vm.taxAmountText = "1.03"
            vm.tipAmountText = "2.00"
            vm.paymentMethod = "Visa"
            vm.notes = "Sample preview data"
            vm.lineItems = [
                ManualReceiptFormViewModel.LineItemDraft(description: "Sandwich", quantityText: "1", unitPriceText: "8.50", totalText: "8.50"),
                ManualReceiptFormViewModel.LineItemDraft(description: "Drink", quantityText: "1", unitPriceText: "3.95", totalText: "3.95")
            ]
            return vm
        }()
    )
}
