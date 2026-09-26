import Foundation

/// Unknown wire values survive decoding; known enums must be rendered exhaustively.
struct APIProblem: Decodable {
    struct Parameters: Decodable {
        let count: Int?
    }

    struct Detail: Decodable {
        let field: String
        let code: String
        let params: Parameters?
    }

    let errorCode: String
    let errorParams: Parameters?
    let errorDetails: [Detail]?

    func message(bundle: Bundle = .main, locale: Locale = .autoupdatingCurrent) -> String {
        guard let code = ApiErrorCode(rawValue: self.errorCode) else {
            return String(
                localized: "Could not complete this request. Check your information and try again.",
                bundle: bundle,
                locale: locale
            )
        }
        if code == .validation_failed {
            let messages = (self.errorDetails ?? []).map { $0.message(bundle: bundle, locale: locale) }
            if !messages.isEmpty {
                return messages.joined(separator: "\n")
            }
        }
        return code.message(count: self.errorParams?.count, bundle: bundle, locale: locale)
    }
}

private extension APIProblem.Detail {
    func message(bundle: Bundle, locale: Locale) -> String {
        let field = (ValidationFieldCode(rawValue: self.field) ?? .base).message(bundle: bundle, locale: locale)
        let rule = ValidationRuleCode(rawValue: self.code) ?? .invalid
        return rule.message(field: field, count: self.params?.count, bundle: bundle, locale: locale)
    }
}
