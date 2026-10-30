import SwiftUI
import UIKit
import AyudixShared

private final class AppHost: ObservableObject {
    let scanner: AppleQrScanner
    let shared: IOSHost
    init() {
        let store = AppleConnectionStore()
        let identity = store.deviceIdentity()
        let scanner = AppleQrScanner(); self.scanner = scanner
        shared = IOSHost(store: store, scanner: scanner, deviceId: identity, deviceName: UIDevice.current.name)
    }
    deinit { shared.close() }
}
private struct SharedView: UIViewControllerRepresentable {
    let host: AppHost
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = host.shared.viewController(); host.scanner.presenter = controller
        return controller
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
@main
struct AyudixApp: App {
    @StateObject private var host = AppHost()
    @Environment(\.scenePhase) private var scene
    var body: some Scene {
        WindowGroup {
            SharedView(host: host).ignoresSafeArea(.keyboard).preferredColorScheme(.dark)
                .onAppear { host.shared.setForeground(active: true) }
                .onChange(of: scene) { _, phase in host.shared.setForeground(active: phase == .active) }
        }
    }
}
