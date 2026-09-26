import Foundation

enum ImportFailure {
    static func message(code: String?) -> String {
        (ImportErrorCode(rawValue: code ?? "") ?? .import_failed).message()
    }
}
