import SwiftUI

struct ShoppingListDisplayItem: Identifiable {
    let id: String
    let name: String
    let details: String?
    let isChecked: Bool
    let onTap: () -> Void
    let onDelete: (() -> Void)?

    init(
        id: String,
        name: String,
        details: String? = nil,
        isChecked: Bool,
        onTap: @escaping () -> Void,
        onDelete: (() -> Void)? = nil
    ) {
        self.id = id
        self.name = name
        self.details = details
        self.isChecked = isChecked
        self.onTap = onTap
        self.onDelete = onDelete
    }
}

struct ShoppingListSectionsContent<HeaderTrailing: View>: View {
    let uncheckedItems: [ShoppingListDisplayItem]
    let checkedItems: [ShoppingListDisplayItem]
    @Binding var checkedSectionExpanded: Bool
    let uncheckedHeaderTrailing: HeaderTrailing

    init(
        uncheckedItems: [ShoppingListDisplayItem],
        checkedItems: [ShoppingListDisplayItem],
        checkedSectionExpanded: Binding<Bool>,
        @ViewBuilder uncheckedHeaderTrailing: () -> HeaderTrailing
    ) {
        self.uncheckedItems = uncheckedItems
        self.checkedItems = checkedItems
        self._checkedSectionExpanded = checkedSectionExpanded
        self.uncheckedHeaderTrailing = uncheckedHeaderTrailing()
    }

    private enum Row: Identifiable {
        case uncheckedHeader
        case checkedHeader
        case item(ShoppingListDisplayItem)

        var id: String {
            switch self {
            case .uncheckedHeader: "header.unchecked"
            case .checkedHeader: "header.checked"
            case let .item(item): item.id
            }
        }
    }

    private var rows: [Row] {
        var rows: [Row] = []
        if !self.uncheckedItems.isEmpty || !self.checkedItems.isEmpty {
            rows.append(.uncheckedHeader)
        }
        rows += self.uncheckedItems.map(Row.item)
        if !self.checkedItems.isEmpty {
            rows.append(.checkedHeader)
            if self.checkedSectionExpanded {
                rows += self.checkedItems.map(Row.item)
            }
        }
        return rows
    }

    var body: some View {
        let rows = self.rows
        VStack(alignment: .leading, spacing: Theme.Spacing.xs + 2) {
            ForEach(rows) { row in
                switch row {
                case .uncheckedHeader:
                    self.uncheckedHeader
                case .checkedHeader:
                    self.checkedHeader
                        .padding(.top, Theme.Spacing.md)
                case let .item(item):
                    ShoppingListItemRow(item: item)
                        .transition(.opacity)
                }
            }
        }
        .animation(.snappy(duration: 0.25), value: rows.map(\.id))
    }

    private var uncheckedHeader: some View {
        HStack {
            Text("To Buy")
                .font(.caption2.weight(.medium))
                .textCase(.uppercase)
                .tracking(1.1)
                .foregroundStyle(Color.mcMuted)
            Spacer()
            self.uncheckedHeaderTrailing
        }
    }

    private var checkedHeader: some View {
        Button {
            withAnimation(.snappy(duration: 0.25)) {
                self.checkedSectionExpanded.toggle()
            }
        } label: {
            HStack(spacing: Theme.Spacing.sm) {
                Text("Already Got")
                    .font(.caption2.weight(.medium))
                    .textCase(.uppercase)
                    .tracking(1.1)
                    .foregroundStyle(Color.mcMuted)

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.mcMuted)
                    .rotationEffect(.degrees(self.checkedSectionExpanded ? 90 : 0))
            }
        }
        .buttonStyle(.plain)
    }
}

private struct ShoppingListItemRow: View {
    let item: ShoppingListDisplayItem

    private var trimmedDetails: String? {
        guard let trimmed = self.item.details?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else { return nil }
        return trimmed
    }

    private var label: Text {
        let name = Text(self.item.name)
            .font(.body)
            .foregroundStyle(self.item.isChecked ? Color.mcMuted : Color.mcInk)
            .strikethrough(self.item.isChecked, color: Color.mcMuted)
        guard let details = self.trimmedDetails else { return name }
        let detailsText = Text(verbatim: "  \(details)")
            .font(.subheadline)
            .foregroundStyle(Color.mcMuted)
        return name + detailsText
    }

    var body: some View {
        Button {
            HapticManager.shared.lightTap()
            self.item.onTap()
        } label: {
            HStack(alignment: .center, spacing: 12) {
                self.checkbox

                self.label
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 0)
            }
            .padding(.horizontal, Theme.Spacing.md)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .fill(self.item.isChecked ? Color.mcSunken : Color.mcSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: Theme.Radius.card)
                    .stroke(Color.mcHairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .geometryGroup()
        .contentShape(RoundedRectangle(cornerRadius: Theme.Radius.card))
        .contextMenu {
            if let onDelete = self.item.onDelete {
                Button(role: .destructive) {
                    onDelete()
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
        }
        .accessibilityLabel(self.item.name)
        .accessibilityValue(self.item.isChecked ? String(localized: "Bought") : String(localized: "To buy"))
        .accessibilityHint(self.item
            .isChecked ? String(localized: "Double-tap to move back to shopping list") :
            String(localized: "Double-tap to mark as bought"))
        .accessibilityAction(named: "Delete") {
            self.item.onDelete?()
        }
    }

    private var checkbox: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(self.item.isChecked ? Color.mcAccent : Color.mcSunken)
            .frame(width: 22, height: 22)
            .overlay {
                if self.item.isChecked {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(Color.mcHairline, lineWidth: 1)
                }
            }
    }
}
