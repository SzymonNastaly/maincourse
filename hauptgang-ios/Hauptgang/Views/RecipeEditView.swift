import os
import PhotosUI
import SwiftUI

private let logger = Logger(subsystem: "app.hauptgang.ios", category: "RecipeEditView")

struct RecipeEditView: View {
    let recipe: RecipeDetail
    var onSave: (() -> Void)?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.displayScale) private var displayScale
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: RecipeEditViewModel

    init(recipe: RecipeDetail, viewModel: RecipeEditViewModel? = nil, onSave: (() -> Void)? = nil) {
        self.recipe = recipe
        self.onSave = onSave
        self._viewModel = State(initialValue: viewModel ?? RecipeEditViewModel())
    }

    var body: some View {
        NavigationStack {
            self.form
        }
    }

    private var form: some View {
        Form {
            self.coverImageSection
            self.nameSection
            self.durationSection
            self.ingredientsSection
            self.instructionsSection
            self.notesSection
            self.sourceSection
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Edit Recipe")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { self.toolbarContent }
        .task {
            self.viewModel.configure(modelContext: self.modelContext)
            self.viewModel.populate(from: self.recipe)
        }
        .onChange(of: self.viewModel.selectedPhoto) { _, _ in
            Task { await self.viewModel.loadPhoto() }
        }
        .onChange(of: self.viewModel.didSave) { _, saved in
            if saved {
                self.onSave?()
                self.dismiss()
            }
        }
        .alert("Error", isPresented: Binding(
            get: { self.viewModel.errorMessage != nil },
            set: {
                if !$0 {
                    self.viewModel.errorMessage = nil
                }
            }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            if let msg = self.viewModel.errorMessage {
                Text(msg)
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { self.dismiss() }
        }
        ToolbarItem(placement: .confirmationAction) {
            Button {
                Task { await self.viewModel.save() }
            } label: {
                if self.viewModel.isSaving {
                    ProgressView()
                        .scaleEffect(0.8)
                } else {
                    Text("Save")
                }
            }
            .disabled(!self.viewModel.isValid || self.viewModel.isSaving)
        }
    }

    private var photoPickerLabel: String {
        self.recipe.heroCoverImageUrl != nil || self.viewModel.hasCoverImageChange
            ? "Change Photo" : "Add Photo"
    }

    // MARK: - Cover Image

    private var coverImageSection: some View {
        Section {
            VStack(spacing: Theme.Spacing.sm) {
                if let data = self.viewModel.coverImageData, let uiImage = UIImage(data: data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipped()
                        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.card))
                } else if let url = Constants.API.resolveURL(self.recipe.heroCoverImageUrl) {
                    CachedRecipeImage(url: url, maxPixelSize: 180 * self.displayScale) { image in
                        image
                            .resizable()
                            .scaledToFill()
                            .frame(height: 180)
                            .frame(maxWidth: .infinity)
                            .clipped()
                    } placeholder: {
                        self.imagePlaceholder
                    } failure: {
                        self.imagePlaceholder
                    }
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.card))
                } else {
                    self.imagePlaceholder
                }

                let pickerLabel = self.photoPickerLabel
                PhotosPicker(selection: self.$viewModel.selectedPhoto, matching: .images) {
                    Label(pickerLabel, systemImage: "photo")
                        .font(.subheadline)
                        .foregroundColor(Color.mcAccent)
                }
            }
        }
        .listRowBackground(Color.clear)
    }

    private var imagePlaceholder: some View {
        RoundedRectangle(cornerRadius: Theme.Radius.card)
            .fill(Color.mcSunken)
            .frame(height: 120)
            .frame(maxWidth: .infinity)
            .overlay {
                Image(systemName: "photo")
                    .font(.system(size: 32))
                    .foregroundColor(Color.mcMuted)
            }
    }

    // MARK: - Name

    private var nameSection: some View {
        Section {
            TextField("Recipe name", text: self.$viewModel.name)
                .font(.body)
        } header: {
            Text("Name")
        } footer: {
            if let error = self.viewModel.nameError, !self.viewModel.name.isEmpty {
                Text(error)
                    .foregroundStyle(Color.mcDanger)
            }
        }
    }

    // MARK: - Duration

    private var durationSection: some View {
        Section("Time & Servings") {
            HStack {
                Label("Prep", systemImage: "clock")
                    .foregroundColor(Color.mcBody)
                Spacer()
                TextField("min", text: self.$viewModel.prepTime)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 60)
            }

            HStack {
                Label("Cook", systemImage: "flame")
                    .foregroundColor(Color.mcBody)
                Spacer()
                TextField("min", text: self.$viewModel.cookTime)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 60)
            }

            HStack {
                Label("Servings", systemImage: "person.2")
                    .foregroundColor(Color.mcBody)
                Spacer()
                TextField("qty", text: self.$viewModel.servings)
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 60)
            }
        }
    }

    // MARK: - Ingredients

    private var ingredientsSection: some View {
        Section {
            ForEach(Array(self.viewModel.ingredients.enumerated()), id: \.offset) { index, _ in
                HStack {
                    TextField(
                        "Ingredient",
                        text: Binding(
                            get: { self.viewModel.ingredients[safe: index] ?? "" },
                            set: { self.viewModel.ingredients[safe: index] = $0 }
                        )
                    )

                    if self.viewModel.ingredients.count > 1 {
                        Button {
                            withAnimation { self.viewModel.removeIngredient(at: index) }
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .foregroundColor(Color.mcDanger)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .onMove { self.viewModel.moveIngredient(from: $0, to: $1) }

            Button {
                withAnimation { self.viewModel.addIngredient() }
            } label: {
                Label("Add Ingredient", systemImage: "plus.circle.fill")
                    .foregroundColor(Color.mcAccent)
            }
        } header: {
            Text("Ingredients")
        }
    }

    // MARK: - Instructions

    private var instructionsSection: some View {
        Section {
            ForEach(Array(self.viewModel.instructions.enumerated()), id: \.offset) { index, _ in
                HStack(alignment: .top) {
                    Text("\(index + 1).")
                        .font(.body)
                        .foregroundColor(Color.mcBody)
                        .padding(.top, 8)

                    TextField(
                        "Step \(index + 1)",
                        text: Binding(
                            get: { self.viewModel.instructions[safe: index] ?? "" },
                            set: { self.viewModel.instructions[safe: index] = $0 }
                        ),
                        axis: .vertical
                    )
                    .lineLimit(1 ... 5)

                    if self.viewModel.instructions.count > 1 {
                        Button {
                            withAnimation { self.viewModel.removeInstruction(at: index) }
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .foregroundColor(Color.mcDanger)
                        }
                        .buttonStyle(.plain)
                        .padding(.top, 6)
                    }
                }
            }
            .onMove { self.viewModel.moveInstruction(from: $0, to: $1) }

            Button {
                withAnimation { self.viewModel.addInstruction() }
            } label: {
                Label("Add Step", systemImage: "plus.circle.fill")
                    .foregroundColor(Color.mcAccent)
            }
        } header: {
            Text("Instructions")
        }
    }

    // MARK: - Notes

    private var notesSection: some View {
        Section("Notes") {
            TextField("Notes", text: self.$viewModel.notes, axis: .vertical)
                .lineLimit(2 ... 8)
        }
    }

    // MARK: - Source

    private var sourceSection: some View {
        Section("Source URL") {
            TextField("https://...", text: self.$viewModel.sourceUrl)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }
}

// MARK: - Safe Array Subscript

private extension Array {
    subscript(safe index: Int) -> Element? {
        get {
            self.indices.contains(index) ? self[index] : nil
        }
        set {
            guard self.indices.contains(index), let newValue else { return }
            self[index] = newValue
        }
    }
}
