import SwiftUI

struct ClipboardPreviewSheet: View {
    let text: String
    let onImport: () -> Void

    @Environment(\.dismiss) private var dismiss
    private var previewLines: String {
        let lines = self.text.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let preview = lines.prefix(10).joined(separator: "\n")
        return preview + "…"
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(self.previewLines)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(10)
                        .fixedSize(horizontal: false, vertical: true)
                } footer: {
                    Text("A recipe will be created from the text in your clipboard.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.mcCanvas)
            .scrollDisabled(true)
            .navigationTitle("Import from Clipboard?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        self.dismiss()
                    }
                    .foregroundStyle(.secondary)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import") {
                        self.onImport()
                    }
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.hidden)
    }
}
