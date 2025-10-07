import Foundation

@discardableResult
private func run() -> Int32 {
    do {
        let input = try readStdin()
        let payload = try decodePayload(from: input)
        let prepared = try RequestPreparation.prepare(from: payload)
        let response = try VisionOcrService().perform(prepared)
        try writeResponse(response)
        return EXIT_SUCCESS
    } catch let decodingError as DecodingError {
        emitError("malformed_request: \(decodingError.localizedDescription)")
        return 2
    } catch let error as OcrError {
        emitError(error.message)
        return error.exitCode
    } catch {
        emitError("unexpected_error: \(error.localizedDescription)")
        return 4
    }
}

private func readStdin() throws -> Data {
    if let data = try FileHandle.standardInput.readToEnd() {
        if data.isEmpty {
            throw OcrError.validation("request body is empty")
        }
        return data
    }
    throw OcrError.validation("failed to read request body")
}

private func decodePayload(from data: Data) throws -> InvocationPayload {
    let decoder = JSONDecoder()
    decoder.keyDecodingStrategy = .useDefaultKeys
    return try decoder.decode(InvocationPayload.self, from: data)
}

private func writeResponse(_ response: OcrResponse) throws {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
    let encoded = try encoder.encode(response)
    if let json = String(data: encoded, encoding: .utf8) {
        FileHandle.standardOutput.write(Data(json.utf8))
        FileHandle.standardOutput.write(Data("\n".utf8))
    } else {
        throw OcrError.internalFailure("failed to encode response")
    }
}

private func emitError(_ message: String) {
    FileHandle.standardError.write(Data((message + "\n").utf8))
}

exit(run())
