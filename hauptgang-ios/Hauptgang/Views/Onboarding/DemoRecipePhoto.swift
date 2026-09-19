import SwiftUI

/// Loose shared JPEG resources are loaded through UIKit, including on iOS 26.
struct DemoRecipePhoto: View {
    let imageName: String
    let height: CGFloat

    var body: some View {
        Color.clear
            .frame(height: self.height)
            .frame(maxWidth: .infinity)
            .background {
                GeometryReader { geometry in
                    Image(uiImage: UIImage(named: self.imageName) ?? UIImage())
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                }
            }
            .clipped()
            .accessibilityLabel("Tomato and chickpea orzo with spinach and basil")
    }
}
