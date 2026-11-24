import SwiftUI

/// Lays words out like a paragraph: left to right, wrapping at the proposed width,
/// each line aligned on its first text baseline. A step with timers uses it so each
/// timer is a real view (a button with its own popover and hit area) that flows
/// with the words around it.
struct WordFlowLayout: Layout {
    /// The width of a space in the words' font.
    var wordSpacing: CGFloat
    /// The font's leading, which a multi-line `Text` adds between its lines.
    var lineSpacing: CGFloat

    struct Line {
        var items: [(index: Int, size: CGSize, baseline: CGFloat)] = []
        var width: CGFloat = 0
        var ascent: CGFloat = 0
        var descent: CGFloat = 0
        var height: CGFloat {
            self.ascent + self.descent
        }
    }

    /// The last arrangement and the width it was made for; measuring every word is
    /// the expensive part, and sizing, placing and aligning ask for the same width.
    struct Cache {
        var width: CGFloat?
        var lines: [Line] = []
    }

    func makeCache(subviews _: Subviews) -> Cache {
        Cache()
    }

    func updateCache(_ cache: inout Cache, subviews _: Subviews) {
        cache = Cache()
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Cache) -> CGSize {
        let lines = self.lines(maxWidth: proposal.width ?? .infinity, subviews: subviews, cache: &cache)
        let width = lines.map(\.width).max() ?? 0
        let height = lines.reduce(0) { $0 + $1.height } + self.lineSpacing * CGFloat(max(0, lines.count - 1))
        return CGSize(width: proposal.width.map { min($0, width) } ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache: inout Cache) {
        var top = bounds.minY
        for line in self.lines(maxWidth: bounds.width, subviews: subviews, cache: &cache) {
            var leading = bounds.minX
            for item in line.items {
                subviews[item.index].place(
                    at: CGPoint(x: leading, y: top + line.ascent - item.baseline),
                    proposal: ProposedViewSize(item.size)
                )
                leading += item.size.width + self.wordSpacing
            }
            top += line.height + self.lineSpacing
        }
    }

    func explicitAlignment(
        of guide: VerticalAlignment,
        in bounds: CGRect,
        proposal _: ProposedViewSize,
        subviews: Subviews,
        cache: inout Cache
    ) -> CGFloat? {
        guard guide == .firstTextBaseline,
              let first = self.lines(maxWidth: bounds.width, subviews: subviews, cache: &cache).first
        else { return nil }
        return bounds.minY + first.ascent
    }

    private func lines(maxWidth: CGFloat, subviews: Subviews, cache: inout Cache) -> [Line] {
        if cache.width == maxWidth {
            return cache.lines
        }
        var lines: [Line] = []
        var line = Line()
        for (index, subview) in subviews.enumerated() {
            let ideal = subview.sizeThatFits(.unspecified)
            // A word wider than the line wraps inside itself.
            let proposal = ideal.width > maxWidth
                ? ProposedViewSize(width: maxWidth, height: nil)
                : ProposedViewSize(ideal)
            let size = ideal.width > maxWidth ? subview.sizeThatFits(proposal) : ideal
            let baseline = subview.dimensions(in: proposal)[VerticalAlignment.firstTextBaseline]

            let neededWidth = line.items.isEmpty ? size.width : line.width + self.wordSpacing + size.width
            if !line.items.isEmpty, neededWidth > maxWidth {
                lines.append(line)
                line = Line()
            }
            line.width = line.items.isEmpty ? size.width : line.width + self.wordSpacing + size.width
            line.items.append((index, size, baseline))
            line.ascent = max(line.ascent, baseline)
            line.descent = max(line.descent, size.height - baseline)
        }
        if !line.items.isEmpty {
            lines.append(line)
        }
        cache = Cache(width: maxWidth, lines: lines)
        return lines
    }
}
