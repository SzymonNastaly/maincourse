import Foundation

struct RecipeSearchSnapshot {
    let id: Int
    let name: String
    let ingredients: [String]
    let instructions: [String]
    let updatedAt: Date
}

enum RecipeFuzzyScorer {
    static func rankedIds(query: String, snapshots: [RecipeSearchSnapshot]) -> [Int] {
        let queryTokenGroups = RecipeSearchQuery.expandedTokenVariants(from: query)
        guard !queryTokenGroups.isEmpty else { return [] }

        let scored: [(Int, Int, Date)] = snapshots.compactMap { snapshot in
            guard !Task.isCancelled else { return nil }

            let tokens = self.searchableTokens(from: snapshot)
            let totalScore = self.totalScore(for: queryTokenGroups, tokens: tokens)
            if totalScore == 0 {
                return nil
            }

            return totalScore > 0 ? (snapshot.id, totalScore, snapshot.updatedAt) : nil
        }

        return scored
            .sorted { lhs, rhs in
                if lhs.1 == rhs.1 {
                    return lhs.2 > rhs.2
                }
                return lhs.1 > rhs.1
            }
            .map(\.0)
    }

    private static func searchableTokens(
        from snapshot: RecipeSearchSnapshot
    ) -> (name: [String], ingredients: [String], instructions: [String]) {
        let nameTokens = RecipeSearchQuery.normalizedTokens(from: snapshot.name)
        let ingredientTokens = RecipeSearchQuery.normalizedTokens(from: snapshot.ingredients.joined(separator: " "))
        let instructionTokens = RecipeSearchQuery.normalizedTokens(from: snapshot.instructions.joined(separator: " "))
        return (nameTokens, ingredientTokens, instructionTokens)
    }

    private static func totalScore(
        for queryTokenGroups: [[[String]]],
        tokens: (name: [String], ingredients: [String], instructions: [String])
    ) -> Int {
        var total = 0

        for variants in queryTokenGroups {
            let bestScore = variants.reduce(0) { best, variant in
                let nameScore = self.fuzzyVariantScore(tokens: variant, recipeTokens: tokens.name, weight: 5)
                let ingredientScore = self.fuzzyVariantScore(
                    tokens: variant,
                    recipeTokens: tokens.ingredients,
                    weight: 3
                )
                let instructionScore = self.fuzzyVariantScore(
                    tokens: variant,
                    recipeTokens: tokens.instructions,
                    weight: 1
                )
                return max(best, nameScore, ingredientScore, instructionScore)
            }

            if bestScore == 0 {
                return 0
            }
            total += bestScore
        }

        return total
    }

    private static func fuzzyVariantScore(tokens: [String], recipeTokens: [String], weight: Int) -> Int {
        guard !tokens.isEmpty else { return 0 }
        var total = 0

        for token in tokens {
            let score = self.fuzzyMatchScore(queryToken: token, tokens: recipeTokens, weight: weight)
            if score == 0 {
                return 0
            }
            total += score
        }

        return total
    }

    private static func fuzzyMatchScore(queryToken: String, tokens: [String], weight: Int) -> Int {
        guard !tokens.isEmpty else { return 0 }
        let maxDist = self.maxEditDistance(for: queryToken)

        for token in tokens {
            if token.contains(queryToken) {
                return weight * 2
            }

            if token.count >= queryToken.count {
                let prefix = String(token.prefix(queryToken.count))
                if self.levenshteinDistance(queryToken, prefix, maxDistance: maxDist) != nil {
                    return weight
                }
            }

            if self.levenshteinDistance(queryToken, token, maxDistance: maxDist) != nil {
                return weight
            }
        }
        return 0
    }

    private static func maxEditDistance(for token: String) -> Int {
        switch token.count {
        case 0 ... 4:
            0
        case 5 ... 7:
            1
        default:
            2
        }
    }

    private static func levenshteinDistance(_ lhs: String, _ rhs: String, maxDistance: Int) -> Int? {
        if lhs == rhs {
            return 0
        }
        if abs(lhs.count - rhs.count) > maxDistance {
            return nil
        }

        let lhsChars = Array(lhs)
        let rhsChars = Array(rhs)
        if lhsChars.isEmpty {
            return rhsChars.count <= maxDistance ? rhsChars.count : nil
        }
        if rhsChars.isEmpty {
            return lhsChars.count <= maxDistance ? lhsChars.count : nil
        }

        var previous = Array(0 ... rhsChars.count)
        var current = Array(repeating: 0, count: rhsChars.count + 1)

        for lhsIndex in 1 ... lhsChars.count {
            current[0] = lhsIndex
            var rowMin = current[0]

            for rhsIndex in 1 ... rhsChars.count {
                let cost = lhsChars[lhsIndex - 1] == rhsChars[rhsIndex - 1] ? 0 : 1
                current[rhsIndex] = min(
                    previous[rhsIndex] + 1,
                    current[rhsIndex - 1] + 1,
                    previous[rhsIndex - 1] + cost
                )
                rowMin = min(rowMin, current[rhsIndex])
            }

            if rowMin > maxDistance {
                return nil
            }
            previous = current
        }

        let distance = previous[rhsChars.count]
        return distance <= maxDistance ? distance : nil
    }
}
