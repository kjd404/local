//
//  FileReceiptImageStore.swift
//  Plutary
//
//  Created by Codex on 9/28/25.
//

import Foundation

enum ReceiptImageStoreError: Error {
    case documentsDirectoryUnavailable
    case writeFailed(underlying: Error)
    case readFailed
    case deleteFailed(underlying: Error)
}

final class FileReceiptImageStore: ReceiptImageStoreProtocol {
    private let fileManager: FileManager
    private let baseURL: URL

    init(fileManager: FileManager = .default, directoryName: String = "ReceiptImages") throws {
        self.fileManager = fileManager
        guard let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            throw ReceiptImageStoreError.documentsDirectoryUnavailable
        }
        self.baseURL = documentsURL.appendingPathComponent(directoryName, isDirectory: true)
        try ensureDirectoryExists()
    }

    func storeImageData(_ data: Data, preferredToken: String?) throws -> String {
        let token = preferredToken ?? UUID().uuidString
        let url = baseURL.appendingPathComponent(token).appendingPathExtension("jpg")
        do {
            try data.write(to: url, options: .atomic)
            return url.lastPathComponent
        } catch {
            throw ReceiptImageStoreError.writeFailed(underlying: error)
        }
    }

    func imageData(for token: String) throws -> Data {
        let url = baseURL.appendingPathComponent(token)
        do {
            return try Data(contentsOf: resolvedURL(from: url))
        } catch {
            throw ReceiptImageStoreError.readFailed
        }
    }

    func deleteImage(for token: String) throws {
        let url = baseURL.appendingPathComponent(token)
        do {
            try fileManager.removeItem(at: resolvedURL(from: url))
        } catch {
            throw ReceiptImageStoreError.deleteFailed(underlying: error)
        }
    }

    // MARK: - Helpers

    private func ensureDirectoryExists() throws {
        if !fileManager.fileExists(atPath: baseURL.path) {
            try fileManager.createDirectory(at: baseURL, withIntermediateDirectories: true, attributes: nil)
        }
    }

    private func resolvedURL(from tokenURL: URL) -> URL {
        if fileManager.fileExists(atPath: tokenURL.path) {
            return tokenURL
        }
        let altURL = tokenURL.appendingPathExtension("jpg")
        return altURL
    }
}
