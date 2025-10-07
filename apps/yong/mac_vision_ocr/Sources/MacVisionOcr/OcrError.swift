import Foundation

enum OcrError: Error {
    case validation(String)
    case vision(String)
    case internalFailure(String)

    var exitCode: Int32 {
        switch self {
        case .validation:
            return 2
        case .vision:
            return 3
        case .internalFailure:
            return 4
        }
    }

    var message: String {
        switch self {
        case let .validation(detail):
            return "validation_error: \(detail)"
        case let .vision(detail):
            return "vision_error: \(detail)"
        case let .internalFailure(detail):
            return "internal_error: \(detail)"
        }
    }
}
