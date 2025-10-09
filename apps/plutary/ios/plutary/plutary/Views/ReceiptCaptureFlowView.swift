//
//  ReceiptCaptureFlowView.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import PhotosUI
import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct ReceiptCaptureFlowView: View {
    @ObservedObject var viewModel: ReceiptCaptureViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedItem: PhotosPickerItem?
    @State private var photoAuthorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    @State private var isPresentingCamera = false

    var body: some View {
        NavigationStack {
            if needsPhotoPermissions {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.system(size: 48))
                        .foregroundStyle(.orange)
                    Text("Photos access needed")
                        .font(.headline)
                    Text("Allow Plutary to read your photo library or camera so we can attach receipt images. You can change this in Settings at any time.")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                    Button("Open Settings") {
                        #if canImport(UIKit)
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                        #endif
                    }
                    .buttonStyle(.borderedProminent)
                    Spacer()
                }
                .padding()
                .navigationTitle("Permissions")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close", role: .cancel) { dismiss() }
                    }
                }
            } else if let reviewViewModel = viewModel.reviewViewModel {
                ReceiptCaptureReviewView(viewModel: reviewViewModel)
            } else if viewModel.isProcessing {
                VStack(spacing: 16) {
                    ProgressView()
                    Text("Running on-device OCR…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .navigationTitle("Processing")
            } else {
                VStack(spacing: 24) {
                    Spacer()
                    Image(systemName: "doc.text.viewfinder")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text("Capture or import a receipt")
                        .font(.title3)
                    Text("Images stay on device. We'll extract merchant, totals, and dates automatically.")
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                    VStack(spacing: 12) {
                        PhotosPicker(
                            selection: $selectedItem,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            Text("Import Photo")
                                .font(.headline)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(RoundedRectangle(cornerRadius: 12).fill(Color.accentColor))
                                .foregroundStyle(.white)
                        }
                        #if canImport(UIKit)
                        if CameraPicker.isAvailable {
                            Button {
                                isPresentingCamera = true
                            } label: {
                                Label("Take Photo", systemImage: "camera")
                                    .font(.headline)
                                    .padding(.horizontal, 24)
                                    .padding(.vertical, 12)
                                    .background(RoundedRectangle(cornerRadius: 12).stroke(Color.accentColor, lineWidth: 1.5))
                            }
                            .accessibilityLabel("Take Photo")
                            .accessibilityHint("Opens the camera to capture a receipt")
                        }
                        #endif
                    }
                    Spacer()
                }
                .navigationTitle("New Receipt")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close", role: .cancel) { dismiss() }
                    }
                }
            }
        }
        .onAppear {
            viewModel.reset()
            viewModel.onComplete = {
                dismiss()
            }
            Task {
                if photoAuthorizationStatus == .notDetermined {
                    let status = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
                    await MainActor.run {
                        photoAuthorizationStatus = status
                    }
                } else {
                    photoAuthorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
                }
            }
        }
        .onDisappear {
            selectedItem = nil
            viewModel.reset()
        }
        .onChange(of: selectedItem) { _, newValue in
            guard let item = newValue else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    await MainActor.run {
                        viewModel.processImageData(data)
                    }
                } else {
                    await MainActor.run {
                        viewModel.alert = ReceiptsListViewModel.AlertState(message: "We couldn't read that image. Try another photo.")
                    }
                }
            }
        }
        .alert("Capture Error", isPresented: Binding(
            get: { viewModel.alert != nil },
            set: { _ in viewModel.alert = nil }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.alert?.message ?? "")
        }
        #if canImport(UIKit)
        .sheet(isPresented: $isPresentingCamera) {
            CameraPicker {
                data in
                viewModel.processImageData(data)
                isPresentingCamera = false
            } onCancel: {
                isPresentingCamera = false
            }
        }
        #endif
    }

    private var needsPhotoPermissions: Bool {
        switch photoAuthorizationStatus {
        case .denied, .restricted:
            return true
        default:
            return false
        }
    }
}

#Preview {
    let controller = PersistenceController.preview
    let vm = ReceiptCaptureViewModel(
        receiptStore: controller.receiptStore,
        imageStore: controller.imageStore,
        ocrService: VisionReceiptOcrService()
    )
    return ReceiptCaptureFlowView(viewModel: vm)
}
