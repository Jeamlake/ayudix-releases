# Código móvil compartido

Base Kotlin Multiplatform para el nuevo cliente iOS y la futura integración gradual de Android. JVM sirve para probar contratos e interfaz en Windows; no crea otro servidor ni sustituye el Windows de Tauri/Rust.

`core` contiene únicamente código común, más los adaptadores HTTP CIO (JVM) y Darwin (iOS) y generación de identificadores. `ui` contiene la interfaz Compose y coordinación de pantallas, sin Android Context, Activities, ContentResolver o Keystore. Las funciones Apple de Keychain y cámara se inyectan como interfaces desde `mobile-ios`.

Las pruebas usan MockEngine, credenciales sintéticas y una memoria simulada: no vinculan teléfonos ni contactan las carpetas reales de la PC. El QR global se usa solo al registrar el nuevo dispositivo; se persiste la credencial individual devuelta por Windows. Los POST inciertos no se repiten automáticamente.

Desde Windows, ejecuta `scripts/test-mobile-shared.ps1` en la raíz del proyecto. Las tareas son `:core:jvmTest` y `:ui:jvmTest`. La vista previa compartida, que no sustituye la prueba Apple, puede renderizarse con `:ui:renderPreview -PayudixPreviewDir=RUTA`.

La integración con Xcode exporta un framework estático `AyudixShared` y usa la integración directa oficial de Kotlin; no requiere CocoaPods ni servicios de IA de pago.
