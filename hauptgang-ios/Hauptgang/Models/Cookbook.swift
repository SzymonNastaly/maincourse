import Foundation

/// Represents a cookbook from GET /api/v1/cookbooks
struct Cookbook: Codable, Identifiable {
    let id: Int
    let name: String
    let personal: Bool
    let recipeCount: Int
    let members: [CookbookMember]
    /// True while a personal cookbook keeps its generated name. Optional so
    /// cached cookbooks and older servers without the key still decode.
    var defaultName: Bool?

    /// Name to show. The stored English default is replaced by the app's own
    /// translation; any other name is user content and shown as typed.
    var displayName: String {
        guard self.defaultName == true else { return self.name }
        return String(
            localized: "My Recipes",
            comment: "Name of each person's personal cookbook while it keeps its default name"
        )
    }
}

/// A member of a cookbook
struct CookbookMember: Codable, Identifiable {
    let id: Int
    let email: String
    let role: String
}
