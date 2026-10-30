# Componentes de la preparación iOS

El código de Ayudix conserva GPL-3.0-only, según `LICENSE`. El icono se deriva de `assets/ayudix-link.svg`, ya perteneciente al proyecto; no se tomó de otra aplicación.

La base utiliza Kotlin/Native y Kotlin Multiplatform, Compose Multiplatform y sus recursos de Material, kotlinx.coroutines, kotlinx.serialization y Ktor, publicados bajo Apache-2.0. Se reutiliza el wrapper Gradle de la aplicación Android, con sus avisos y checksum de distribución. XcodeGen, utilizado para generar el proyecto en macOS, se distribuye bajo MIT.

PyYAML 6.0.3 (MIT) se instaló únicamente como herramienta de validación en la carpeta de herramientas de esta PC; no se empaqueta en la app. FFmpeg ya existente y Chromium sin interfaz se utilizaron únicamente para exportar el icono propio; no se añaden a la aplicación Apple.

Antes de distribuir un binario Apple hay que conservar los avisos correspondientes a las versiones resueltas y revisar los manifiestos de privacidad de las dependencias y la licencia de cualquier cliente de streaming que se incorpore. No se incluyó el código Android de Moonlight en este cliente iOS.
