import Foundation

struct User: Codable, Identifiable, Equatable {
    let id: Int
    let email: String
    var name: String?
    /// Whether the backend may send this user lifecycle nudges. Defaults to true to
    /// match the server column default, and so a cached user decoded from an older
    /// build (before login returned the field) does not read as opted out.
    var lifecycleNotificationsEnabled: Bool = true
}

extension User {
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(Int.self, forKey: .id)
        self.email = try container.decode(String.self, forKey: .email)
        self.name = try container.decodeIfPresent(String.self, forKey: .name)
        self.lifecycleNotificationsEnabled =
            try container.decodeIfPresent(Bool.self, forKey: .lifecycleNotificationsEnabled) ?? true
    }
}
