import CoreGraphics
import Foundation
import ImageIO
import Vision

struct InvocationPayload: Decodable {
    let imagePngBase64: String
    let locale: String?
    let minimumConfidence: Float?
    let recognitionLevel: String?

    enum CodingKeys: String, CodingKey {
        case imagePngBase64 = "image_png_base64"
        case locale
        case minimumConfidence = "minimum_confidence"
        case recognitionLevel = "recognition_level"
    }
}

struct VisionConfiguration {
    let localeIdentifier: String
    let minimumConfidence: Float
    let recognitionLevel: VNRequestTextRecognitionLevel
}

struct PreparedRequest {
    let image: CGImage
    let configuration: VisionConfiguration
}

struct OcrResponse: Encodable {
    let text: String
    let warnings: [String]
}

enum RequestPreparation {
    static func prepare(from payload: InvocationPayload) throws -> PreparedRequest {
        guard !payload.imagePngBase64.isEmpty else {
            throw OcrError.validation("image_png_base64 must not be empty")
        }

        guard let decoded = Data(base64Encoded: payload.imagePngBase64) else {
            throw OcrError.validation("image_png_base64 is not valid base64")
        }

        guard let source = CGImageSourceCreateWithData(decoded as CFData, nil) else {
            throw OcrError.validation("image data is not a recognized image format")
        }

        guard let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw OcrError.validation("unable to decode image data")
        }

        let locale = (payload.locale?.isEmpty == false) ? payload.locale! : "en_US"
        let minimumConfidence = payload.minimumConfidence ?? 0.3
        if minimumConfidence < 0.0 || minimumConfidence > 1.0 {
            throw OcrError.validation("minimum_confidence must be between 0.0 and 1.0")
        }

        let recognitionIdentifier = payload.recognitionLevel?.lowercased() ?? "accurate"
        guard let recognitionLevel = recognitionLevel(from: recognitionIdentifier) else {
            throw OcrError.validation("recognition_level must be 'accurate' or 'fast'")
        }

        let configuration = VisionConfiguration(
            localeIdentifier: locale,
            minimumConfidence: minimumConfidence,
            recognitionLevel: recognitionLevel
        )

        return PreparedRequest(image: image, configuration: configuration)
    }

    private static func recognitionLevel(from value: String) -> VNRequestTextRecognitionLevel? {
        switch value {
        case "accurate":
            return .accurate
        case "fast":
            return .fast
        default:
            return nil
        }
    }
}
