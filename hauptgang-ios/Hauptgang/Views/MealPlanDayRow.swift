import SwiftUI

struct MealPlanDayRow: View {
    private static let entryRowHeight: CGFloat = 56
    private static let dayNumberWidth: CGFloat = 48
    private static let dayNumberHorizontalOffset: CGFloat = 4

    @Environment(NetworkMonitor.self) private var networkMonitor

    let dateString: String
    let entries: [PersistedMealPlanEntry]
    let onAddTapped: () -> Void
    let onDeleteEntry: (PersistedMealPlanEntry) -> Void
    let onToggleVote: (PersistedMealPlanEntry) -> Void

    private var components: MealPlanViewModel.DayComponents {
        MealPlanViewModel.dayComponents(for: self.dateString)
    }

    private var canDelete: Bool {
        !self.components.isPast && !self.networkMonitor.isOffline
    }

    private var pastForegroundColor: Color {
        Color.mcMuted.opacity(0.7)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            self.header
                .padding(.horizontal, Theme.Spacing.md)
                .padding(.top, Theme.Spacing.xs)

            if !self.entries.isEmpty {
                self.entryList
                    .padding(.top, Theme.Spacing.sm)
            }

            Divider()
                .padding(.horizontal, Theme.Spacing.xl)
                .padding(.top, self.entries.isEmpty ? Theme.Spacing.md : 0)
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .center, spacing: Theme.Spacing.md) {
            Text(self.components.dayNumber)
                .font(.system(size: 36, weight: .regular))
                .foregroundStyle(
                    self.components.isPast
                        ? self.pastForegroundColor
                        : (self.components.isToday ? Color.mcAccent : Color.mcInk)
                )
                .frame(minWidth: Self.dayNumberWidth, alignment: .leading)
                .offset(x: Self.dayNumberHorizontalOffset)

            VStack(alignment: .leading, spacing: 2) {
                Text(self.components.weekday.uppercased())
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(
                        self.components.isPast
                            ? self.pastForegroundColor
                            : Color.mcInk
                    )
                Text(self.components.month.uppercased())
                    .font(.caption)
                    .foregroundStyle(
                        self.components.isPast
                            ? self.pastForegroundColor
                            : Color.mcMuted
                    )
            }

            Spacer()

            if !self.components.isPast {
                Button {
                    self.onAddTapped()
                } label: {
                    Image(systemName: "plus")
                        .font(.title3.weight(.regular))
                        .foregroundStyle(Color.mcAccent)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(self.networkMonitor.isOffline)
            }
        }
    }

    // MARK: - Entries

    private var entryList: some View {
        List {
            ForEach(self.entries, id: \.scopedId) { entry in
                self.entryRow(entry)
                    .listRowInsets(EdgeInsets(
                        top: 0,
                        leading: Theme.Spacing.md,
                        bottom: 0,
                        trailing: Theme.Spacing.md
                    ))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        if self.canDelete {
                            Button(role: .destructive) {
                                self.onDeleteEntry(entry)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.mcCanvas)
        .listStyle(.plain)
        .scrollDisabled(true)
        .frame(height: CGFloat(self.entries.count) * Self.entryRowHeight)
    }

    private func entryRow(_ entry: PersistedMealPlanEntry) -> some View {
        ZStack {
            // Keep the link invisible so the row can navigate without List showing the default chevron accessory.
            NavigationLink(value: entry.recipeId) {
                EmptyView()
            }
            .opacity(0)

            HStack(spacing: Theme.Spacing.md) {
                VStack(alignment: .leading, spacing: Theme.Spacing.xs) {
                    Text(entry.recipeName)
                        .font(.body)
                        .foregroundStyle(
                            self.components.isPast
                                ? self.pastForegroundColor
                                : Color.mcInk
                        )
                        .lineLimit(2)

                    if entry.syncState == .pendingCreate {
                        Text("Syncing...")
                            .font(.caption)
                            .foregroundStyle(
                                self.components.isPast
                                    ? self.pastForegroundColor
                                    : Color.mcMuted
                            )
                    }
                }

                Spacer()

                self.voteButton(entry)
            }
            .padding(.leading, Self.dayNumberWidth + Theme.Spacing.md)
            .padding(.vertical, Theme.Spacing.sm)
        }
    }

    private func voteButton(_ entry: PersistedMealPlanEntry) -> some View {
        Button {
            self.onToggleVote(entry)
        } label: {
            HStack(spacing: Theme.Spacing.xs) {
                Text(entry.voteCount > 0 ? "\(entry.voteCount)" : "")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(
                        self.components.isPast
                            ? self.pastForegroundColor
                            : Color.mcBody
                    )
                    .frame(minWidth: 16, alignment: .trailing)
                Image(systemName: entry.votedByCurrentUser ? "heart.fill" : "heart")
                    .foregroundStyle(
                        self.components.isPast
                            ? self.pastForegroundColor
                            : (entry.votedByCurrentUser ? Color.mcAccent : Color.mcMuted)
                    )
                    .font(.title3)
            }
        }
        .disabled(
            self.networkMonitor.isOffline
                || entry.syncState == .pendingCreate
                || self.components.isPast
        )
        .buttonStyle(.plain)
    }
}
