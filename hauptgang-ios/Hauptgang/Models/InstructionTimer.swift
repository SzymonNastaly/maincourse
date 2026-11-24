import Foundation

/// A duration the server found in a recipe step, from `instruction_timers`.
/// `text` is copied verbatim from the step, so the client can find it there;
/// `label` is already in the recipe's language ("Simmer", "Köcheln lassen").
struct InstructionTimer: Codable, Hashable {
    let text: String
    let minSeconds: Int
    let maxSeconds: Int
    let label: String
    /// The duration applies to each side or piece in turn.
    let perSide: Bool

    init(text: String, minSeconds: Int, maxSeconds: Int, label: String, perSide: Bool = false) {
        self.text = text
        self.minSeconds = minSeconds
        self.maxSeconds = maxSeconds
        self.label = label
        self.perSide = perSide
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.text = try container.decode(String.self, forKey: .text)
        self.minSeconds = try container.decode(Int.self, forKey: .minSeconds)
        self.maxSeconds = try container.decodeIfPresent(Int.self, forKey: .maxSeconds) ?? self.minSeconds
        self.label = try container.decode(String.self, forKey: .label)
        self.perSide = try container.decodeIfPresent(Bool.self, forKey: .perSide) ?? false
    }

    var isRange: Bool {
        self.maxSeconds > self.minSeconds
    }

    /// What a running timer is called: the label, plus "per side" when it applies.
    var timerLabel: String {
        self.perSide ? String(localized: "\(self.label) per side") : self.label
    }

    /// The times a range offers: shortest, middle (when it is a whole minute and the
    /// range spans at least two), longest. A single value offers itself.
    var choices: [Int] {
        guard self.isRange else { return [self.minSeconds] }
        let middle = (self.minSeconds + self.maxSeconds) / 2
        let hasMiddle = self.maxSeconds - self.minSeconds >= 120 && middle.isMultiple(of: 60)
        return hasMiddle ? [self.minSeconds, middle, self.maxSeconds] : [self.minSeconds, self.maxSeconds]
    }
}
