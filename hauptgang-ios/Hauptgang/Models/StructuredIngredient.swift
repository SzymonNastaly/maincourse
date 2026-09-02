import Foundation

/// A structured ingredient row from `/api/v1/recipes/:id` `structured_ingredients[]`.
///
/// Wire format note: `amount` and `amount_max` arrive as **JSON strings**
/// (e.g. `"0.5"`, `"200.0"`) because Rails serializes `BigDecimal` as a
/// string. Decoding via the default `Decimal` path would fail, so we read
/// them as `String?` and convert with `Decimal(string:)`.
struct StructuredIngredient: Codable, Identifiable, Hashable {
    let id: Int
    let position: Int
    let amount: Decimal?
    let amountMax: Decimal?
    let unit: String?
    let name: String?
    let note: String?
    let raw: String

    /// True if this row has been parsed by `ParseRecipeIngredientsJob`
    /// (mirrors the server-side `Ingredient#parsed?` predicate).
    var hasStructuredFields: Bool {
        self.amount != nil || self.amountMax != nil || (self.unit?.isEmpty == false)
    }

    init(
        id: Int,
        position: Int,
        amount: Decimal? = nil,
        amountMax: Decimal? = nil,
        unit: String? = nil,
        name: String? = nil,
        note: String? = nil,
        raw: String
    ) {
        self.id = id
        self.position = position
        self.amount = amount
        self.amountMax = amountMax
        self.unit = unit
        self.name = name
        self.note = note
        self.raw = raw
    }

    /// Note: The shared `APIClient` decoder uses `.convertFromSnakeCase`, so JSON
    /// `amount_max` becomes `amountMax` before key lookup. Raw values therefore
    /// match the camelCase property names. PersistedRecipe stores the JSON via
    /// `JSONEncoder`/`JSONDecoder` *without* snake_case conversion, so the
    /// round-trip uses camelCase keys end-to-end.
    private enum CodingKeys: String, CodingKey {
        case id, position, amount, amountMax, unit, name, note, raw
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(Int.self, forKey: .id)
        self.position = try container.decode(Int.self, forKey: .position)
        self.unit = try container.decodeIfPresent(String.self, forKey: .unit)
        self.name = try container.decodeIfPresent(String.self, forKey: .name)
        self.note = try container.decodeIfPresent(String.self, forKey: .note)
        self.raw = try container.decode(String.self, forKey: .raw)
        self.amount = try Self.decodeDecimal(container, key: .amount)
        self.amountMax = try Self.decodeDecimal(container, key: .amountMax)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(self.id, forKey: .id)
        try container.encode(self.position, forKey: .position)
        try container.encodeIfPresent(self.unit, forKey: .unit)
        try container.encodeIfPresent(self.name, forKey: .name)
        try container.encodeIfPresent(self.note, forKey: .note)
        try container.encode(self.raw, forKey: .raw)
        // Preserve the wire format (decimal-as-string) for round-trip safety.
        try container.encodeIfPresent(self.amount.map { "\($0)" }, forKey: .amount)
        try container.encodeIfPresent(self.amountMax.map { "\($0)" }, forKey: .amountMax)
    }

    private static func decodeDecimal(
        _ container: KeyedDecodingContainer<CodingKeys>,
        key: CodingKeys
    ) throws -> Decimal? {
        guard container.contains(key) else { return nil }
        if try container.decodeNil(forKey: key) {
            return nil
        }

        // Wire format is String. Fall back to Decimal/Double for resilience
        // (e.g. cached responses written before the wire format was firmed up).
        if let string = try? container.decode(String.self, forKey: key) {
            return string.isEmpty ? nil : Decimal(string: string)
        }
        if let decimal = try? container.decode(Decimal.self, forKey: key) {
            return decimal
        }
        if let double = try? container.decode(Double.self, forKey: key) {
            return Decimal(double)
        }
        return nil
    }
}
