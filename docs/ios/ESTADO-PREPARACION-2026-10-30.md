# Preparación iOS — 30 de octubre de 2026

## Resultado comprobado en Windows

Se añadieron `mobile-ios` y `mobile-shared` al repositorio local existente de Ayudix Link. La base de contratos y coordinación pasó **22 pruebas JVM**: 18 de protocolo y 4 de estado/almacenamiento simulado. La interfaz compartida compiló y se renderizó sin abrir ventanas; esa imagen es una vista previa del código común, no una captura de un iPhone.

La comprobación estática de YAML/plist verificó proyecto, rutas, QR v2, permisos declarados, privacidad, integridad del wrapper y límites del workflow. El script macOS pasó el análisis de sintaxis con Bash. Estos resultados no equivalen a compilar Swift/UIKit, ejecutar Xcode ni validar Keychain/cámara reales.

La aplicación Windows instalada continúa en alpha.26. Esta preparación no recompila ni reinstala Windows/Android, no cambia sus datos y no crea ni envía una APK.

## Alcance implementado de la base

- Registro propio del iPhone, sustitución del secreto del QR por credencial individual y almacenamiento preparado con Keychain `ThisDeviceOnly`.
- Identidad y selección de PC independientes de su nombre o IP; verificación de identidad antes de enviar credenciales a otra ruta.
- Explorador de carpetas y listado de archivos mediante las API existentes de Windows.
- Chat de texto con el motor de la PC, sesión explícita, actualización mientras está visible y acceso al último mensaje.
- Bloqueo de escrituras si el llavero no puede leerse; sin sobrescribir conexiones previas.
- Rechazo de redirecciones autenticadas y de destinos públicos no admitidos. El chat exige Tailscale o HTTPS.
- Cámara y Keychain Apple implementados en Swift, pendientes de validación nativa.

No están implementados todavía el envío/descarga de archivos con recibo, reproducción, voz, streaming remoto, historial paginado ni los controles móviles completos de planes, rutinas y permisos.

## Organización de esta PC

| Elemento | Ubicación |
| --- | --- |
| Proyecto único | `C:\Desarrollo\Proyectos\ayudix-link` |
| JDK existente | `C:\Desarrollo\Herramientas\Java\jdk-21` |
| Gradle existente | `C:\Desarrollo\Herramientas\Gradle\gradle-9.4.1` |
| Validador YAML aislado | `C:\Desarrollo\Herramientas\Ayudix-iOS\Validacion\python` |
| Instalador PyYAML, SHA-256 contrastado con PyPI | `C:\Desarrollo\Instaladores\Python\PyYAML` |
| Dependencias Gradle/Maven | `C:\Desarrollo\Cache\Gradle` |
| Caché Kotlin/Native fijada | `C:\Desarrollo\Cache\KotlinNative\ayudix-link` |
| Caché pip fijada | `C:\Desarrollo\Cache\pip` |
| Informes y vista previa | `C:\Desarrollo\Logs\Ayudix\iOS-Preparacion-2026-10-30` |

No se instalaron Xcode ni un simulador Apple en Windows. El propietario eligió AltStore gratuito. Se descargó el instalador oficial de AltServer en `C:\Desarrollo\Instaladores\Ayudix-iOS\AltServer`; no está instalado y sus archivos no tienen firma Authenticode. No se introdujeron credenciales Apple.

## Compilación Apple y repositorio

El repositorio local no tenía un remoto `origin`. El repositorio de GitHub encontrado para Ayudix es **Jeamlake/ayudix-releases**, público. No se añade por suposición un remoto al árbol local con cambios históricos pendientes ni se publica ese árbol completo.

La preparación se exporta con una lista explícita de fuentes nuevas y revisión de contenido. El workflow local está en `.github/workflows/ayudix-ios.yml`; para revisión sin activar Actions puede guardarse como `docs/ios/ayudix-ios.workflow.yml`. Esa copia es inerte y no concede permisos.

El propietario completó la autorización oficial de GitHub CLI con el alcance `workflow`. Se activó la compilación manual en `main`, que lee un commit concreto de la rama iOS sin fusionarla. Esta autorización pertenece a la herramienta de desarrollo y no amplía los permisos del conector GitHub de Ayudix.

Primera ejecución nativa: https://github.com/Jeamlake/ayudix-releases/actions/runs/41086799889. Fuentes seleccionadas: `4c5cdecf4f6e78c037dbe2544efe83cab7cbf3f7`. La preparación permanece en el borrador https://github.com/Jeamlake/ayudix-releases/pull/1. Los resultados nativos se añadirán al finalizar la ejecución; su arranque no acredita todavía éxito o un IPA instalable.

## Pendiente para entregar al iPhone 17

1. Activar y ejecutar el workflow en macOS 26 con Xcode 26.4.x; compilar los enlaces Kotlin/Swift y ejecutar las tres pruebas Apple escritas.
2. Corregir cualquier diferencia nativa detectada y validar orientación, fuentes, teclado, accesibilidad y permisos.
3. Elegir firma/distribución: pruebas personales con renovación periódica o Apple Developer para TestFlight/App Store. El IPA de dispositivo que pueda producir el workflow es **sin firma**, no instalable directamente.
4. Probar la vinculación y el comportamiento de cámara/red/Keychain en el teléfono físico. No hay un IPA firmado ni una instalación acreditada en esta preparación.
5. Incorporar transferencias verificadas, multimedia, voz y escritorio por etapas; no trasladar el módulo Android de streaming como si fuera binario iOS.

La [guía de instalación desde Windows](INSTALAR-DESDE-WINDOWS.md) explica la firma personal elegida con AltStore y la prueba física pendiente.
