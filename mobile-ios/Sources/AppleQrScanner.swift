import UIKit
import AVFoundation
import AyudixShared

final class AppleQrScanner: NSObject, QrScanner {
    weak var presenter: UIViewController?
    private var presenting = false
    func start(callback: QrResult) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self, !self.presenting else { callback.cancelled(); return }
            guard let presenter = self.presenter, presenter.presentedViewController == nil else {
                callback.failed(message: "Cierra el diálogo abierto y vuelve a escanear."); return
            }
            let open: (Bool) -> Void = { [weak self, weak presenter] allowed in
                DispatchQueue.main.async {
                    guard let self = self, let presenter = presenter else { callback.cancelled(); return }
                    guard allowed else { callback.failed(message: "Permite la cámara en Ajustes para escanear el QR. También puedes pegar el código de vinculación."); return }
                    self.presenting = true
                    let scanner = QrCameraController { [weak self] value, error in
                        self?.presenting = false
                        if let error = error { callback.failed(message: error) }
                        else if let value = value { callback.received(value: value) }
                        else { callback.cancelled() }
                    }
                    scanner.modalPresentationStyle = .fullScreen
                    presenter.present(scanner, animated: true)
                }
            }
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized: open(true)
            case .notDetermined: AVCaptureDevice.requestAccess(for: .video, completionHandler: open)
            default: open(false)
            }
        }
    }
}

private final class QrCameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "pe.ayudix.qr-camera")
    private let completion: (String?, String?) -> Void
    private var preview: AVCaptureVideoPreviewLayer?
    private var finished = false
    init(completion: @escaping (String?, String?) -> Void) { self.completion = completion; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("Use init(completion:)") }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        let close = UIButton(type: .system)
        close.setTitle("Cancelar", for: .normal); close.tintColor = .white
        close.addTarget(self, action: #selector(cancel), for: .touchUpInside)
        close.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(close)
        let instructions = UILabel(); instructions.text = "Escanea el QR de Ayudix en tu PC"
        instructions.textColor = .white; instructions.numberOfLines = 0; instructions.textAlignment = .center
        instructions.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(instructions)
        NSLayoutConstraint.activate([
            close.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            close.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            close.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
            instructions.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            instructions.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            instructions.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -36)
        ])
        guard let camera = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: camera), session.canAddInput(input) else {
            finish(nil, "No se pudo abrir la cámara del iPhone."); return
        }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { finish(nil, "Este dispositivo no pudo iniciar el lector QR."); return }
        session.addOutput(output); output.setMetadataObjectsDelegate(self, queue: .main); output.metadataObjectTypes = [.qr]
        let layer = AVCaptureVideoPreviewLayer(session: session); layer.videoGravity = .resizeAspectFill
        view.layer.insertSublayer(layer, at: 0); preview = layer
        queue.async { [session] in session.startRunning() }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview?.frame = view.bounds }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated); queue.async { [session] in session.stopRunning() }
        if !finished { finished = true; completion(nil, nil) }
    }
    @objc private func cancel() { finish(nil, nil) }
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let text = (objects.first as? AVMetadataMachineReadableCodeObject)?.stringValue else { return }
        finish(text, nil)
    }
    private func finish(_ value: String?, _ error: String?) {
        guard !finished else { return }; finished = true
        queue.async { [session] in session.stopRunning() }
        if presentingViewController != nil { dismiss(animated: true) { [completion] in completion(value, error) } }
        else { completion(value, error) }
    }
}
