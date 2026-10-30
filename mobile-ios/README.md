# Ayudix para iPhone y iPad — preparación 0.1.0

Este cliente se desarrolla en el mismo repositorio que Windows y Android. No es una conversión de APK, no sustituye la versión Android instalada y todavía no es una entrega iOS firmada.

## Carpetas

- `mobile-ios/Sources`: entrada SwiftUI, Keychain y lector de QR con AVFoundation.
- `mobile-ios/Resources`: permisos y manifiesto de privacidad de esta preparación.
- `mobile-ios/Tests`: pruebas de inicio y navegación para el simulador Apple.
- `mobile-ios/project.yml`: proyecto Xcode descrito con XcodeGen; se genera en la Mac, sin archivos personales de Xcode.
- `mobile-shared/core`: contratos, reglas de red, vinculación, identidad de PC, cliente de carpetas y chat, compartibles con Android.
- `mobile-shared/ui`: interfaz Compose Multiplatform, coordinación de peticiones y entrada UIKit.
- `scripts/test-mobile-shared.ps1`: prueba local desde Windows con las herramientas organizadas existentes.
- `scripts/ios/build-ios.sh`: compilación y pruebas Apple en macOS.
- `.github/workflows/ayudix-ios.yml`: ejecución manual en un runner estándar macOS de un repositorio público; no contiene certificados ni cuentas Apple.

## Alcance real

La base implementa la lectura del QR v2 de Windows, registro de un dispositivo propio, cambio entre PC, almacenamiento de identidad y credenciales en Keychain `ThisDeviceOnly`, listado de carpetas autorizadas, navegación por subcarpetas, chat de texto y seguimiento del asistente mientras la app está visible. El chat usa Tailscale o HTTPS; no degrada a HTTP Wi-Fi sin cifrar. Una dirección que responde con otra identidad de datos no recibe la credencial guardada.

El explorador de esta preparación lista archivos; todavía no descarga, mueve, comparte ni reproduce. Voz, transferencias con recibo, historial paginado, edición de permisos/rutinas, controles de planes y escritorio en directo requieren etapas posteriores. No hay botones que afirmen haber completado esas operaciones.

## Preparar desde Windows

```powershell
& 'C:\Desarrollo\Proyectos\ayudix-link\scripts\test-mobile-shared.ps1'
```

Usa JDK 21 y Gradle 9.4.1 de `C:\Desarrollo\Herramientas`. Maven/Gradle conservan las dependencias en `C:\Desarrollo\Cache\Gradle`; Kotlin/Native se dirige a `C:\Desarrollo\Cache\KotlinNative\ayudix-link`. Los registros de esta preparación están en `C:\Desarrollo\Logs\Ayudix\iOS-Preparacion-2026-10-30`. No se instala Xcode en Windows ni se instala AltServer en esta etapa.

Se fija Kotlin 2.4.10 para esta base independiente, compatible con Gradle 9.4.1 y Xcode 26.4.x. La aplicación Android existente conserva su configuración. La integración futura de ese cliente con `mobile-shared` requerirá su propia migración y pruebas; no se afirma que ya consuma el nuevo módulo.

## Compilar en macOS

Requisitos: Xcode **26.4.x**, Java 21 y XcodeGen. Selecciona el Xcode correspondiente mediante `DEVELOPER_DIR`. En una Mac propia, indica un simulador disponible:

```bash
export DEVELOPER_DIR=/Applications/Xcode_26.4.app/Contents/Developer
export AYUDIX_SIMULATOR_UDID="UUID-del-simulador-iPhone-17"
/bin/bash scripts/ios/build-ios.sh simulator
/bin/bash scripts/ios/build-ios.sh device
```

El flujo de GitHub elige un simulador disponible de iPhone 17. Solo se activa manualmente, con permisos de lectura del repositorio, límite de 45 minutos y retención de artefactos de tres días. Se detiene si el repositorio es privado o falta el Xcode fijado; no cambia a un runner de pago ni transmite credenciales de esta PC.

La salida de dispositivo será `release/ios-preparation/Ayudix-iOS-0.1.0-preparation-UNSIGNED.ipa` **si la compilación Apple pasa**. Un ZIP de fuentes o un IPA sin firma no acredita instalación ni funcionamiento en el iPhone. No se creó un IPA en Windows.

## Firma y distribución pendientes

La [guía de instalación desde Windows](../docs/ios/INSTALAR-DESDE-WINDOWS.md) separa la firma personal de la compilación y detalla la prueba física.

Para pruebas personales desde Windows, una opción es AltServer/AltStore Classic con renovación de firma cada siete días. Para TestFlight/App Store se necesita la cuenta y membresía Apple correspondiente. El propietario debe introducir sus credenciales únicamente en el flujo elegido; no se incluyen en el código, en GitHub Actions ni en el chat.

Antes de distribuir hay que validar la primera compilación y las pruebas Apple, la firma, la cámara y red local en el iPhone 17, los permisos, orientación/teclado, los manifiestos de privacidad de las dependencias, el icono exportado y la forma de actualizar. No se publica ni instala automáticamente este cliente desde el canal Android/Windows.
