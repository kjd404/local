import Foundation
import Vision

struct VisionOcrService {
    func perform(_ request: PreparedRequest) throws -> OcrResponse {
        let visionRequest = VNRecognizeTextRequest()
        visionRequest.recognitionLanguages = [request.configuration.localeIdentifier]
        visionRequest.recognitionLevel = request.configuration.recognitionLevel
        visionRequest.usesLanguageCorrection = true

        let handler = VNImageRequestHandler(cgImage: request.image, options: [:])

        do {
            try handler.perform([visionRequest])
        } catch {
            throw OcrError.vision("Vision OCR failed: \(error.localizedDescription)")
        }

        guard let observations = visionRequest.results else {
            return OcrResponse(text: "", warnings: ["no_text_detected"])
        }

        var lines: [String] = []
        var droppedLowConfidence = false

        for observation in observations {
            guard observation.confidence >= request.configuration.minimumConfidence else {
                droppedLowConfidence = true
                continue
            }

            guard let candidate = observation.topCandidates(1).first else {
                continue
            }

            let trimmed = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                lines.append(trimmed)
            }
        }

        let text = lines.joined(separator: "\n")
        var warnings: [String] = []

        if droppedLowConfidence {
            warnings.append("low_confidence_filtered")
        }
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            warnings.append("no_text_detected")
        }

        return OcrResponse(text: text, warnings: warnings)
    }
}
