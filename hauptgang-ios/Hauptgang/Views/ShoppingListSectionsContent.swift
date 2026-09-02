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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

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

    private var gridColumns: [GridItem] {
        if self.horizontalSizeClass == .compact {
            Array(repeating: GridItem(.flexible(), spacing: Theme.Spacing.sm), count: 3)
        } else {
            [GridItem(.adaptive(minimum: 140, maximum: 200), spacing: Theme.Spacing.sm)]
        }
    }

    private var shouldShowUncheckedSection: Bool {
        !self.uncheckedItems.isEmpty || !self.checkedItems.isEmpty
    }

    var body: some View {
        LazyVStack(alignment: .leading, spacing: Theme.Spacing.lg) {
            if self.shouldShowUncheckedSection {
                self.uncheckedSection
            }

            if !self.checkedItems.isEmpty {
                self.checkedSection
            }
        }
    }

    private var uncheckedSection: some View {
        Section {
            if !self.uncheckedItems.isEmpty {
                LazyVGrid(columns: self.gridColumns, spacing: Theme.Spacing.sm) {
                    ForEach(self.uncheckedItems) { item in
                        ShoppingListItemTile(item: item)
                            .transition(.asymmetric(
                                insertion: .scale(scale: 0.5).combined(with: .opacity),
                                removal: .identity
                            ))
                    }
                }
                .animation(.snappy(duration: 0.25), value: self.uncheckedItems.map(\.id))
            }
        } header: {
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
    }

    private var checkedSection: some View {
        Section {
            if self.checkedSectionExpanded {
                LazyVGrid(columns: self.gridColumns, spacing: Theme.Spacing.sm) {
                    ForEach(self.checkedItems) { item in
                        ShoppingListItemTile(item: item)
                            .transition(.asymmetric(
                                insertion: .scale(scale: 0.5).combined(with: .opacity),
                                removal: .identity
                            ))
                    }
                }
                .animation(.snappy(duration: 0.25), value: self.checkedItems.map(\.id))
            }
        } header: {
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
}

private struct ShoppingListItemTile: View {
    let item: ShoppingListDisplayItem

    var body: some View {
        Button {
            HapticManager.shared.lightTap()
            self.item.onTap()
        } label: {
            VStack(spacing: 2) {
                Text(self.item.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)

                if let trimmed = self.item.details?
                    .trimmingCharacters(in: .whitespacesAndNewlines),
                    !trimmed.isEmpty {
                    Text(trimmed)
                        .font(.caption.weight(.light))
                        .italic()
                        .foregroundStyle(Color.mcBody)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                }
            }
            .multilineTextAlignment(.center)
            .foregroundStyle(self.item.isChecked ? Color.mcMuted : Color.mcInk)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(Theme.Spacing.sm)
            .aspectRatio(1, contentMode: .fit)
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
        .accessibilityValue(self.item.isChecked ? "Bought" : "To buy")
        .accessibilityHint(self.item
            .isChecked ? "Double-tap to move back to shopping list" : "Double-tap to mark as bought")
        .accessibilityAction(named: "Delete") {
            self.item.onDelete?()
        }
    }
}
