//
//  ReceiptsListView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import SwiftUI

struct ReceiptsListView: View {
    @ObservedObject var viewModel: ReceiptsListViewModel
    let persistenceController: PersistenceController

    @State private var manualReceiptForm: ManualReceiptFormViewModel?
    @State private var captureViewModel: ReceiptCaptureViewModel?
    @State private var showingAddOptions = false

    init(viewModel: ReceiptsListViewModel, persistenceController: PersistenceController) {
        self.viewModel = viewModel
        self.persistenceController = persistenceController
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.receipts.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: 48))
                            .foregroundStyle(.secondary)
                        Text("No receipts yet")
                            .font(.headline)
                        Text("Add one manually or capture a photo to get started.")
                            .multilineTextAlignment(.center)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding()
                } else {
                    List {
                        ForEach(viewModel.receipts) { receipt in
                            NavigationLink {
                                ReceiptDetailView(
                                    viewModel: ReceiptDetailViewModel(receipt: receipt),
                                    imageStore: persistenceController.imageStore
                                )
                            } label: {
                                ReceiptRowView(receipt: receipt)
                            }
                        }
                        .onDelete(perform: viewModel.removeReceipts)
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Receipts")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showingAddOptions = true
                    } label: {
                        Label("Add Receipt", systemImage: "plus.circle.fill")
                    }
                }
            }
            .confirmationDialog("Add a receipt", isPresented: $showingAddOptions, titleVisibility: .visible) {
                Button("Scan receipt") {
                    presentCaptureFlow()
                }
                Button("Enter manually") {
                    presentManualForm()
                }
            }
            .sheet(item: $manualReceiptForm) { formViewModel in
                ManualReceiptFormView(viewModel: formViewModel)
            }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.alert != nil },
                set: { _ in viewModel.alert = nil }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.alert?.message ?? "")
            }
            .onAppear {
                viewModel.onAppear()
            }
            .onDisappear {
                viewModel.onDisappear()
            }
        }
        .sheet(item: $captureViewModel) { capture in
            ReceiptCaptureFlowView(viewModel: capture)
                .onDisappear {
                    captureViewModel = nil
                }
        }
    }

    private func presentManualForm() {
        let manual = ManualReceiptFormViewModel(receiptStore: persistenceController.receiptStore)
        manual.onSaveSuccess = {
            manualReceiptForm = nil
        }
        manualReceiptForm = manual
    }

    private func presentCaptureFlow() {
        let ocrService = VisionReceiptOcrService()
        let capture = ReceiptCaptureViewModel(
            receiptStore: persistenceController.receiptStore,
            imageStore: persistenceController.imageStore,
            ocrService: ocrService
        )
        capture.onComplete = {
            captureViewModel = nil
        }
        captureViewModel = capture
    }
}

#Preview {
    let controller = PersistenceController.preview
    let vm = ReceiptsListViewModel(receiptStore: controller.receiptStore)
    return ReceiptsListView(viewModel: vm, persistenceController: controller)
}
