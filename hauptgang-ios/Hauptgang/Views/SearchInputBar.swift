import SwiftUI
import UIKit

struct SearchInputBar: View {
    @Binding var text: String
    let prompt: String
    var icon: String = "magnifyingglass"
    var onSubmit: (() -> Void)?
    var onCancel: (() -> Void)?
    var keepFocusOnSubmit: Bool = false
    @State private var isFocused: Bool = false

    var body: some View {
        HStack(spacing: Theme.Spacing.sm) {
            HStack(spacing: Theme.Spacing.sm) {
                Image(systemName: self.icon)
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Color(.placeholderText))

                NonDismissingTextField(
                    text: self.$text,
                    isFocused: self.$isFocused,
                    prompt: self.prompt,
                    returnKeyType: self.resolvedReturnKey,
                    dismissOnReturn: !self.keepFocusOnSubmit,
                    onSubmit: { self.onSubmit?() }
                )
                .frame(height: 22)
            }
            .padding(.horizontal, Theme.Spacing.sm + 2)
            .padding(.vertical, Theme.Spacing.sm + 2)
            .background(Color(.tertiarySystemFill), in: .capsule)

            if self.isFocused {
                Button {
                    self.text = ""
                    self.isFocused = false
                    self.onCancel?()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(Color(.placeholderText))
                }
                .buttonStyle(.plain)
                .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .padding(.horizontal, Theme.Spacing.md)
        .padding(.bottom, Theme.Spacing.md)
        .animation(.easeInOut(duration: 0.2), value: self.isFocused)
    }

    private var resolvedReturnKey: UIReturnKeyType {
        if self.keepFocusOnSubmit {
            return .continue
        }
        if self.onSubmit != nil {
            return .done
        }
        return .search
    }
}

private struct NonDismissingTextField: UIViewRepresentable {
    @Binding var text: String
    @Binding var isFocused: Bool
    let prompt: String
    let returnKeyType: UIReturnKeyType
    let dismissOnReturn: Bool
    let onSubmit: () -> Void

    func makeUIView(context: Context) -> UITextField {
        let tf = UITextField()
        tf.placeholder = self.prompt
        tf.font = .systemFont(ofSize: 17)
        tf.delegate = context.coordinator
        tf.returnKeyType = self.returnKeyType
        tf.autocorrectionType = .default
        tf.spellCheckingType = .default
        tf.addTarget(
            context.coordinator,
            action: #selector(Coordinator.textChanged(_:)),
            for: .editingChanged
        )
        return tf
    }

    func updateUIView(_ uiView: UITextField, context: Context) {
        if uiView.text != self.text {
            uiView.text = self.text
        }
        if uiView.placeholder != self.prompt {
            uiView.placeholder = self.prompt
        }
        if uiView.returnKeyType != self.returnKeyType {
            uiView.returnKeyType = self.returnKeyType
        }
        context.coordinator.onSubmit = self.onSubmit
        context.coordinator.dismissOnReturn = self.dismissOnReturn

        if self.isFocused && !uiView.isFirstResponder {
            DispatchQueue.main.async {
                uiView.becomeFirstResponder()
            }
        } else if !self.isFocused && uiView.isFirstResponder {
            DispatchQueue.main.async {
                uiView.resignFirstResponder()
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(
            text: self.$text,
            isFocused: self.$isFocused,
            onSubmit: self.onSubmit,
            dismissOnReturn: self.dismissOnReturn
        )
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        @Binding var text: String
        @Binding var isFocused: Bool
        var onSubmit: () -> Void
        var dismissOnReturn: Bool

        init(
            text: Binding<String>,
            isFocused: Binding<Bool>,
            onSubmit: @escaping () -> Void,
            dismissOnReturn: Bool
        ) {
            self._text = text
            self._isFocused = isFocused
            self.onSubmit = onSubmit
            self.dismissOnReturn = dismissOnReturn
        }

        @objc func textChanged(_ textField: UITextField) {
            self.text = textField.text ?? ""
        }

        func textFieldDidBeginEditing(_: UITextField) {
            if !self.isFocused {
                self.isFocused = true
            }
        }

        func textFieldDidEndEditing(_: UITextField) {
            if self.isFocused {
                self.isFocused = false
            }
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            self.onSubmit()
            if self.dismissOnReturn {
                textField.resignFirstResponder()
            }
            return false
        }
    }
}
