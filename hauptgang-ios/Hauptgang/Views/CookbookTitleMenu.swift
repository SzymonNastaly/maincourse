import SwiftUI

struct CookbookTitleMenu: View {
    let cookbooks: [Cookbook]
    let activeCookbookId: Int?
    let onSelect: (Cookbook) -> Void

    var body: some View {
        ForEach(self.cookbooks) { cookbook in
            CookbookTitleMenuButton(
                cookbook: cookbook,
                activeCookbookId: self.activeCookbookId,
                onSelect: self.onSelect
            )
        }
    }
}

private struct CookbookTitleMenuButton: View {
    let cookbook: Cookbook
    let activeCookbookId: Int?
    let onSelect: (Cookbook) -> Void

    private var isActive: Bool {
        self.cookbook.id == self.activeCookbookId
    }

    private var systemImage: String {
        if self.isActive {
            return "checkmark"
        }

        return self.cookbook.personal ? "person.fill" : "person.2.fill"
    }

    var body: some View {
        Button {
            self.onSelect(self.cookbook)
        } label: {
            Label(self.cookbook.displayName, systemImage: self.systemImage)
        }
        .disabled(self.isActive)
    }
}
