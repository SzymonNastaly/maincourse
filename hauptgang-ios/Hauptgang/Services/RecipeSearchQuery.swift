import Foundation

enum RecipeSearchQuery {
    private static let englishSynonyms: [String: [String]] = [
        "scallion": ["green onion", "spring onion"],
        "chili": ["chile"],
        "chile": ["chili"],
        "coriander": ["cilantro"],
        "cilantro": ["coriander"],
        "garbanzo": ["chickpea"],
        "chickpea": ["garbanzo"],
        "aubergine": ["eggplant"],
        "eggplant": ["aubergine"],
        "courgette": ["zucchini"],
        "zucchini": ["courgette"],
        "capsicum": ["bell pepper"],
        "minced": ["ground"],
        "ground": ["minced"],
        "prawn": ["shrimp"],
        "shrimp": ["prawn"],
        "rocket": ["arugula"],
        "arugula": ["rocket"],
        "yogurt": ["yoghurt"],
        "yoghurt": ["yogurt"],
        "ketchup": ["catsup"],
        "bicarbonate": ["baking soda"],
        "soda": ["bicarbonate", "bicarb"]
    ]

    /// Regional names for the same ingredient; each single-word member finds the
    /// others. Polish stems like "kartofl" cover forms that drop a vowel
    /// ("kartofle"), which prefix matching on "kartofel" would miss.
    private static let synonymGroups: [[String]] = [
        // German, Austrian and Swiss names
        ["kartoffel", "erdapfel"],
        ["tomate", "paradeiser"],
        ["blumenkohl", "karfiol"],
        ["rosenkohl", "kohlsprossen"],
        ["sahne", "rahm", "obers"],
        ["quark", "topfen"],
        ["aprikose", "marille"],
        ["pflaume", "zwetschge", "zwetschke"],
        ["meerrettich", "kren"],
        ["puderzucker", "staubzucker"],
        ["eiweiß", "eiklar"],
        ["semmelbrösel", "paniermehl"],
        ["brötchen", "semmel", "schrippe"],
        ["lauch", "porree"],
        ["frühlingszwiebel", "lauchzwiebel"],
        ["aubergine", "melanzani"],
        ["zucchini", "zucchetti"],
        ["karotte", "möhre", "mohrrübe", "rüebli"],
        ["hackfleisch", "faschiertes", "gehacktes"],
        ["rucola", "rauke"],
        ["pfifferling", "eierschwammerl"],
        ["pilz", "schwammerl"],
        ["johannisbeere", "ribisel"],
        ["feldsalat", "vogerlsalat", "nüsslisalat"],
        ["rotkohl", "rotkraut", "blaukraut"],
        ["weißkohl", "weißkraut"],
        ["natron", "backsoda", "speisesoda"],
        ["joghurt", "jogurt"],
        // Polish names
        ["ziemniak", "kartofel", "kartofl", "pyra", "pyry"],
        ["ciecierzyca", "cieciorka"],
        ["bakłażan", "oberżyna"],
        ["rukola", "rokietta"],
        ["twaróg", "twarożek"]
    ]

    private static let synonymMap: [String: [String]] = {
        var map = englishSynonyms
        for group in synonymGroups {
            for term in group {
                let key = searchableText(term)
                map[key, default: []] += group.filter { $0 != term }
            }
        }
        return map
    }()

    /// The form both the search index and queries are compared in: no case, no
    /// accents, ß as "ss" and ł as "l" (neither folding nor FTS5's
    /// remove_diacritics touches ł, so "bulka" would otherwise miss "bułka").
    static func searchableText(_ raw: String) -> String {
        raw
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
            .lowercased()
            .replacingOccurrences(of: "ł", with: "l")
    }

    static func normalizedTokens(from raw: String) -> [String] {
        self.searchableText(raw)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
    }

    static func expandedTokenVariants(from raw: String) -> [[[String]]] {
        let tokens = self.normalizedTokens(from: raw)
        guard !tokens.isEmpty else { return [] }

        return tokens.map { token in
            var variants: [[String]] = [[token]]

            if let synonyms = self.synonyms(for: token) {
                let synonymTokens = synonyms.map { self.normalizedTokens(from: $0) }.filter { !$0.isEmpty }
                variants.append(contentsOf: synonymTokens)
            }

            return variants
        }
    }

    /// Exact match first, then a known name the token extends by a plural or case
    /// ending ("tomaten", "ziemniaków", "shrimps"). Short names need an exact
    /// match so "rahm" doesn't claim "rahmspinat".
    private static func synonyms(for token: String) -> [String]? {
        if let synonyms = synonymMap[token] {
            return synonyms
        }

        let stem = self.synonymMap.keys
            .filter { $0.count >= 5 && token.hasPrefix($0) && token.count - $0.count <= 3 }
            .max { $0.count < $1.count }
        return stem.flatMap { self.synonymMap[$0] }
    }

    static func buildFTSQuery(from raw: String) -> String? {
        let groups = self.expandedTokenVariants(from: raw)
        guard !groups.isEmpty else { return nil }

        let clauses = groups.map { group in
            let variants = group.map { variant in
                if variant.count == 1 {
                    return "\(variant[0])*"
                }
                let terms = variant.map { "\($0)*" }
                return "(" + terms.joined(separator: " AND ") + ")"
            }
            return variants.joined(separator: " OR ")
        }

        return clauses.map { "(\($0))" }.joined(separator: " AND ")
    }
}
