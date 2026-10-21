import SwiftUI
import Shared

@main
struct CasaApp: App {
    // "SwiftUI." porque o Kotlin também exporta uma classe Scene (cenas da casa).
    var body: some SwiftUI.Scene {
        WindowGroup {
            ComposeView()
                // O Compose cuida das margens seguras (notch, barra inicial, teclado).
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}

/// Tela inteira em Compose Multiplatform (o mesmo código do app Android).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(videoFactory: VlcVideoFactory())
        controller.view.backgroundColor = UIColor(red: 0.08, green: 0.086, blue: 0.11, alpha: 1)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
