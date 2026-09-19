@testable import Hauptgang
import Testing

@MainActor
struct CookbookViewModelTests {
    private func makePersonalCookbook(id: Int = 1) -> Cookbook {
        Cookbook(id: id, name: "My Recipes", personal: true, recipeCount: 5, members: [
            CookbookMember(id: 100, email: "me@example.com", role: "owner")
        ])
    }

    private func makeSharedCookbook(
        id: Int = 2,
        ownerId: Int = 100,
        ownerEmail: String = "me@example.com",
        collaboratorId: Int = 200,
        collaboratorEmail: String = "friend@example.com"
    ) -> Cookbook {
        Cookbook(id: id, name: "Family Recipes", personal: false, recipeCount: 3, members: [
            CookbookMember(id: ownerId, email: ownerEmail, role: "owner"),
            CookbookMember(id: collaboratorId, email: collaboratorEmail, role: "collaborator")
        ])
    }

    @Test func delayedCancelledRefreshCannotApplyToAnotherAccount() async {
        let gate = HeldCookbookResponse()
        let service = MockCookbookService()
        service.fetchHandler = { await gate.fetch() }
        let viewModel = CookbookViewModel(service: service)
        viewModel.configure(userId: 1)
        let task = Task { await viewModel.refresh() }
        await gate.waitForRequest()
        task.cancel()
        await viewModel.reset()
        viewModel.configure(userId: 2)
        await gate.release([self.makePersonalCookbook(id: 10)])
        await task.value
        #expect(viewModel.cookbooks.isEmpty)
        #expect(viewModel.activeCookbook == nil)
    }

    // MARK: - isSharedCookbookOwner

    @Test func isSharedCookbookOwner_trueWhenCurrentUserIsOwner() async {
        let mock = MockCookbookService()
        mock.cookbooksToReturn = [self.makePersonalCookbook(), self.makeSharedCookbook(ownerId: 100)]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.isSharedCookbookOwner == true)
    }

    @Test func isSharedCookbookOwner_falseWhenCurrentUserIsCollaborator() async {
        let mock = MockCookbookService()
        mock.cookbooksToReturn = [
            self.makePersonalCookbook(),
            self.makeSharedCookbook(ownerId: 100, collaboratorId: 200)
        ]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 200)
        await vm.loadCookbooks()

        #expect(vm.isSharedCookbookOwner == false)
    }

    @Test func isSharedCookbookOwner_falseWhenNoSharedCookbook() async {
        let mock = MockCookbookService()
        mock.cookbooksToReturn = [self.makePersonalCookbook()]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.isSharedCookbookOwner == false)
    }

    @Test func isSharedCookbookOwner_falseWhenNotConfigured() async {
        let mock = MockCookbookService()
        mock.cookbooksToReturn = [self.makePersonalCookbook(), self.makeSharedCookbook()]

        let vm = CookbookViewModel(service: mock)
        // No configure() call
        await vm.loadCookbooks()

        #expect(vm.isSharedCookbookOwner == false)
    }

    // MARK: - loadCookbooks defaults

    @Test func loadCookbooks_prefersSharedCookbookAsDefault() async {
        // Reset CookbookContext so no saved selection interferes
        await CookbookContext.shared.reset()

        let mock = MockCookbookService()
        let personal = self.makePersonalCookbook()
        let shared = self.makeSharedCookbook()
        mock.cookbooksToReturn = [personal, shared]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.activeCookbook?.id == shared.id)
    }

    @Test func loadCookbooks_fallsBackToPersonalWhenNoShared() async {
        let mock = MockCookbookService()
        let personal = self.makePersonalCookbook()
        mock.cookbooksToReturn = [personal]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.activeCookbook?.id == personal.id)
    }

    // MARK: - handleForbidden

    @Test func handleForbidden_resetsToPersonalCookbook() async {
        // Reset CookbookContext so no saved selection interferes
        await CookbookContext.shared.reset()

        let mock = MockCookbookService()
        let personal = self.makePersonalCookbook()
        let shared = self.makeSharedCookbook()
        mock.cookbooksToReturn = [personal, shared]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.activeCookbook?.id == shared.id)

        // Simulate being kicked — now only personal is returned
        mock.cookbooksToReturn = [personal]
        await vm.handleForbidden()

        #expect(vm.activeCookbook?.id == personal.id)
        #expect(vm.cookbooks.count == 1)
    }

    // MARK: - setActiveCookbook

    @Test func setActiveCookbook_returnsFalseForSameCookbook() async {
        let mock = MockCookbookService()
        let personal = self.makePersonalCookbook()
        mock.cookbooksToReturn = [personal]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        let changed = await vm.setActiveCookbook(personal)
        #expect(changed == false)
    }

    @Test func setActiveCookbook_returnsTrueForDifferentCookbook() async {
        // Reset CookbookContext so loadCookbooks defaults to shared
        await CookbookContext.shared.reset()

        let mock = MockCookbookService()
        let personal = self.makePersonalCookbook()
        let shared = self.makeSharedCookbook()
        mock.cookbooksToReturn = [personal, shared]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        // Active should be shared (default preference), switching to personal should return true
        let changed = await vm.setActiveCookbook(personal)
        #expect(changed == true)
        #expect(vm.activeCookbook?.id == personal.id)
    }

    // MARK: - reset

    @Test func reset_clearsAllState() async {
        let mock = MockCookbookService()
        mock.cookbooksToReturn = [self.makePersonalCookbook(), self.makeSharedCookbook()]

        let vm = CookbookViewModel(service: mock)
        vm.configure(userId: 100)
        await vm.loadCookbooks()

        #expect(vm.cookbooks.count == 2)

        await vm.reset()

        #expect(vm.cookbooks.isEmpty)
        #expect(vm.activeCookbook == nil)
        #expect(vm.isSharedCookbookOwner == false)
    }
}

private actor HeldCookbookResponse {
    private var response: CheckedContinuation<[Cookbook], Never>?
    private var waiter: CheckedContinuation<Void, Never>?

    func fetch() async -> [Cookbook] {
        await withCheckedContinuation { continuation in
            self.response = continuation
            self.waiter?.resume()
            self.waiter = nil
        }
    }

    func waitForRequest() async {
        if self.response != nil {
            return
        }
        await withCheckedContinuation { self.waiter = $0 }
    }

    func release(_ cookbooks: [Cookbook]) {
        self.response?.resume(returning: cookbooks)
        self.response = nil
    }
}
