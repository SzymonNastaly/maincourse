import SwiftUI

struct RecipeWelcomeView<ImportActions: View>: View {
    let showsExample: Bool
    let onTryExample: () -> Void
    let onDismissExample: () -> Void
    @ViewBuilder let importActions: () -> ImportActions

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Save recipes")
                        .font(.title2.bold())
                        .foregroundStyle(Color.mcInk)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Menu(content: self.importActions) {
                        Label("Save your own recipe", systemImage: "plus")
                    }
                    .primaryButton()
                    Text("or")
                        .font(.subheadline)
                        .foregroundStyle(Color.mcBody)
                        .frame(maxWidth: .infinity)
                    Text("In Instagram or your browser, tap Share, then choose MainCourse.")
                        .font(.body)
                        .foregroundStyle(Color.mcInk)
                }

                if self.showsExample {
                    self.exampleCard
                }
            }
            .padding(24)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(Color.mcCanvas)
    }

    private var exampleCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            DemoRecipePhoto(imageName: "tomato-orzo-v1.jpg", height: 180)
                .accessibilityHidden(true)
                .overlay(alignment: .topTrailing) {
                    Button(action: self.onDismissExample) {
                        Image(systemName: "xmark")
                            .font(.subheadline.weight(.semibold))
                            .frame(width: 44, height: 44)
                            .background(Color.mcSurface, in: Circle())
                    }
                    .padding(10)
                    .accessibilityLabel("Dismiss example suggestion")
                }
            VStack(alignment: .leading, spacing: 10) {
                Text("Try one together")
                    .font(.title3.weight(.semibold))
                Text("Turn this post into a recipe. A few taps, and dinner is in your cookbook.")
                    .font(.subheadline)
                    .foregroundStyle(Color.mcBody)
                Button(action: self.onTryExample) {
                    HStack {
                        Text("Try the interactive example")
                        Spacer()
                        Image(systemName: "arrow.right")
                    }.frame(minHeight: 44)
                }
                .font(.subheadline.weight(.semibold))
                .tint(Color.mcAccent)
                .accessibilityIdentifier("recipes.try-example")
            }.padding(20)
        }
        .background(Color.mcSurface)
        .clipShape(.rect(cornerRadius: Theme.Radius.panel))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel)
            .stroke(Color.mcHairline, lineWidth: 1))
    }
}

#Preview {
    RecipeWelcomeView(showsExample: true, onTryExample: {}, onDismissExample: {}, importActions: {
        Button("Paste from Clipboard") {}
    })
}
