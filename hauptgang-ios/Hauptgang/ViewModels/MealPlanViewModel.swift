import Foundation
import os
import SwiftData
import SwiftUI

@MainActor @Observable
final class MealPlanViewModel {
    private(set) var visibleDates: [String] = []
    private(set) var entriesByDate: [String: [PersistedMealPlanEntry]] = [:]
    private(set) var isSyncing = false
    var didReceiveForbidden = false

    private let initialPast = 1
    private let initialFuture = 8

    private var activeCookbookId: Int?
    private let repository: MealPlanRepositoryProtocol
    private let service: MealPlanServiceProtocol
    private let networkMonitor: any NetworkStatusProviding
    private let logger = Logger(subsystem: "app.hauptgang.ios", category: "MealPlanViewModel")

    init(
        repository: MealPlanRepositoryProtocol? = nil,
        service: MealPlanServiceProtocol = MealPlanService.shared,
        networkMonitor: any NetworkStatusProviding = NetworkMonitor.shared
    ) {
        self.repository = repository ?? MealPlanRepository()
        self.service = service
        self.networkMonitor = networkMonitor
    }

    func configure(modelContext: ModelContext) {
        self.repository.configure(modelContext: modelContext)
    }

    // MARK: - Refresh

    func refresh(cookbookId: Int) async {
        guard !self.isSyncing else { return }

        self.activeCookbookId = cookbookId
        self.isSyncing = true
        defer { self.isSyncing = false }

        guard let window = self.currentVisibleWindow() else { return }

        self.visibleDates = window.dates
        self.loadCachedData()

        guard !self.networkMonitor.isOffline else { return }

        await self.syncPendingEntries(cookbookId: cookbookId)

        do {
            let plans = try await service.fetchMealPlans(
                cookbookId: cookbookId,
                from: Self.dateString(for: window.start),
                to: Self.dateString(for: window.end)
            )
            try self.repository.saveDays(plans, cookbookId: cookbookId)
            self.loadCachedData()
        } catch {
            self.logger.error("Failed to refresh meal plans: \(error.localizedDescription)")
            if let apiError = error as? APIError, case .forbidden = apiError {
                self.didReceiveForbidden = true
            }
        }
    }

    // MARK: - Add Entry

    func addEntry(cookbookId: Int, date: String, recipe: PersistedRecipe) {
        guard !self.networkMonitor.isOffline else { return }

        do {
            try self.repository.addLocalEntry(
                cookbookId: cookbookId,
                date: date,
                recipeId: recipe.id,
                recipeName: recipe.name,
                recipeCoverImageUrl: recipe.thumbnailCoverImageUrl
            )
            self.loadCachedData()
            Task { await self.syncAddEntry(cookbookId: cookbookId, date: date, recipeId: recipe.id) }
        } catch {
            self.logger.error("Failed to add entry locally: \(error.localizedDescription)")
        }
    }

    private func syncAddEntry(cookbookId: Int, date: String, recipeId: Int) async {
        do {
            let updatedDay = try await service.addEntry(cookbookId: cookbookId, date: date, recipeId: recipeId)
            try self.repository.saveDays([updatedDay], cookbookId: cookbookId)
            self.loadCachedData()
        } catch {
            self.logger.error("Failed to sync add entry: \(error.localizedDescription)")
            if let apiError = error as? APIError {
                switch apiError {
                case .unprocessableEntity, .notFound:
                    try? self.repository.deletePendingEntry(cookbookId: cookbookId, date: date, recipeId: recipeId)
                    self.loadCachedData()
                default:
                    break
                }
            }
        }
    }

    // MARK: - Delete Entry

    func deleteEntry(_ entry: PersistedMealPlanEntry, cookbookId: Int) {
        guard !self.networkMonitor.isOffline else { return }

        guard let serverId = entry.serverId else {
            try? self.repository.deletePendingEntry(
                cookbookId: entry.cookbookId,
                date: entry.date,
                recipeId: entry.recipeId
            )
            self.loadCachedData()
            return
        }

        Task {
            do {
                try await self.service.deleteEntry(id: serverId)
                try? self.repository.deletePendingEntry(
                    cookbookId: entry.cookbookId,
                    date: entry.date,
                    recipeId: entry.recipeId
                )
                self.loadCachedData()
            } catch {
                self.logger.error("Failed to delete entry from server: \(error.localizedDescription)")
                await self.refresh(cookbookId: cookbookId)
            }
        }
    }

    // MARK: - Vote

    func toggleVote(entry: PersistedMealPlanEntry, cookbookId: Int) {
        guard !self.networkMonitor.isOffline, let serverId = entry.serverId else { return }

        let wasVoted = entry.votedByCurrentUser
        entry.votedByCurrentUser = !wasVoted
        entry.voteCount += wasVoted ? -1 : 1

        Task {
            do {
                let updatedDay: MealPlanDay = if wasVoted {
                    try await self.service.unvote(entryId: serverId)
                } else {
                    try await self.service.vote(entryId: serverId)
                }
                try self.repository.saveDays([updatedDay], cookbookId: cookbookId)
                self.loadCachedData()
            } catch {
                self.logger.error("Failed to toggle vote: \(error.localizedDescription)")
                entry.votedByCurrentUser = wasVoted
                entry.voteCount += wasVoted ? 1 : -1
            }
        }
    }

    // MARK: - Reset

    func resetForCookbookSwitch() {
        self.activeCookbookId = nil
        self.visibleDates = []
        self.entriesByDate = [:]
        self.isSyncing = false
    }

    func clearData() {
        do {
            try self.repository.clearAll()
            self.visibleDates = []
            self.entriesByDate = [:]
        } catch {
            self.logger.error("Failed to clear meal plan data: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private func loadCachedData() {
        guard let cookbookId = self.activeCookbookId, !self.visibleDates.isEmpty else { return }

        self.entriesByDate = [:]

        do {
            var newEntries: [String: [PersistedMealPlanEntry]] = [:]
            for date in self.visibleDates {
                let entries = try self.repository.getEntries(cookbookId: cookbookId, date: date)
                newEntries[date] = entries.sorted { $0.voteCount > $1.voteCount }
            }
            self.entriesByDate = newEntries
        } catch {
            self.logger.error("Failed to load cached meal plan data: \(error.localizedDescription)")
        }
    }

    private func currentVisibleWindow() -> (start: Date, end: Date, dates: [String])? {
        let today = Date()
        let cal = Calendar.current
        guard
            let start = cal.date(byAdding: .day, value: -self.initialPast, to: today),
            let end = cal.date(byAdding: .day, value: self.initialFuture, to: today)
        else {
            return nil
        }

        return (start: start, end: end, dates: Self.datesInRange(from: start, to: end))
    }

    private func syncPendingEntries(cookbookId: Int) async {
        do {
            let pending = try repository.getPendingEntries(cookbookId: cookbookId)
            for entry in pending {
                do {
                    let updatedDay = try await service.addEntry(
                        cookbookId: cookbookId,
                        date: entry.date,
                        recipeId: entry.recipeId
                    )
                    try self.repository.saveDays([updatedDay], cookbookId: cookbookId)
                } catch {
                    self.logger.error("Failed to sync pending entry: \(error.localizedDescription)")
                    if let apiError = error as? APIError {
                        switch apiError {
                        case .unprocessableEntity, .notFound:
                            try? self.repository.deletePendingEntry(
                                cookbookId: cookbookId,
                                date: entry.date,
                                recipeId: entry.recipeId
                            )
                        default:
                            break
                        }
                    }
                }
            }
        } catch {
            self.logger.error("Failed to get pending entries: \(error.localizedDescription)")
        }
    }

    // MARK: - Date helpers

    static func dateString(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.string(from: date)
    }

    static func date(from dateString: String) -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.date(from: dateString)
    }

    struct DayComponents {
        let dayNumber: String
        let weekday: String
        let month: String
        let isToday: Bool
        let isPast: Bool
    }

    static func dayComponents(for dateString: String) -> DayComponents {
        guard let date = Self.date(from: dateString) else {
            return DayComponents(dayNumber: dateString, weekday: "", month: "", isToday: false, isPast: false)
        }
        let cal = Calendar.current
        let isToday = cal.isDateInToday(date)
        let isPast = cal.startOfDay(for: date) < cal.startOfDay(for: Date())

        let dayF = DateFormatter()
        dayF.dateFormat = "dd"
        let weekdayF = DateFormatter()
        weekdayF.dateFormat = "EEEE"
        let monthF = DateFormatter()
        monthF.dateFormat = "MMMM"

        return DayComponents(
            dayNumber: dayF.string(from: date),
            weekday: weekdayF.string(from: date),
            month: monthF.string(from: date),
            isToday: isToday,
            isPast: isPast
        )
    }

    private static func datesInRange(from start: Date, to end: Date) -> [String] {
        let cal = Calendar.current
        let startDay = cal.startOfDay(for: start)
        let endDay = cal.startOfDay(for: end)
        var dates: [String] = []
        var current = startDay
        while current <= endDay {
            dates.append(Self.dateString(for: current))
            guard let next = cal.date(byAdding: .day, value: 1, to: current) else { break }
            current = next
        }
        return dates
    }
}
