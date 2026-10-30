# Preparación iOS — 30 de octubre de 2026

## Resultado comprobado en Windows

Se añadieron `mobile-ios` y `mobile-shared` al repositorio local existente de Ayudix Link. La base de contratos y coordinación pasó **22 pruebas JVM**: 18 de protocolo y 4 de estado/almacenamiento simulado. La interfaz compartida compiló y se renderizó sin abrir ventanas; esa imagen es una vista previa del código común, no una captura de un iPhone.

La comprobación estática de YAML/plist verificó proyecto, rutas, QR v2, permisos declarados, privacidad, integridad del wrapper y límites del workflow. El script macOS pasó el análisis de sintaxis con Bash. Estas comprobaciones locales se distinguen de los resultados Apple siguientes.

## Resultado Apple comprobado

La ejecución https://github.com/Jeamlake/ayudix-releases/actions/runs/41089550514 compiló Kotlin/Native y Swift, y pasó **3/3 pruebas XCTest de interfaz** en un simulador **iPhone 17 con iOS 26.4.1**. Sus fuentes son `6e4e82fdc59ce922ab287968b9221199d293b064`; el resumen exportado por Xcode declara `Passed`, tres pruebas aprobadas y cero fallos. Se conservaron capturas del inicio, archivos sin vincular y chat sin vincular. Estas pruebas no simulan una conexión real con la PC ni prueban cámara física, transferencias o la cuenta Apple.

Se corrigió la propiedad ProMotion obligatoria de Compose en `Info.plist`. El paso de dispositivo posterior se detuvo en una tubería del script de comprobación de Xcode; se sustituyó por lectura completa de la salida. La continuación de dispositivo verificó que todos los archivos de `mobile-ios` y `mobile-shared` fueran idénticos a la ejecución de simulador aprobada antes de omitir su repetición.

La ejecución https://github.com/Jeamlake/ayudix-releases/actions/runs/41092884011 terminó **correctamente** y generó `Ayudix-iOS-0.1.0-preparation-UNSIGNED.ipa`. Su SHA-256 es `80f8616ef6fc6adb02444b0633cd9f80eaaa035e9ef400892529ceab0b97963c`. El verificador local comprobó ZIP, recibo del commit `35408f086ecbf594af09cb8fe2786823c4bce059`, identificador `pe.ayudix.link.ios`, versión 0.1.0/1, ejecutable Mach-O **arm64 para iPhoneOS**, mínimo iOS 17 y SDK 26.4.0. No es un binario de simulador. No contiene perfil Apple ni firma de distribución: todavía necesita firmarse con AltStore y validarse en el teléfono físico.

La aplicación Windows instalada continúa en alpha.26. Esta preparación no recompila ni reinstala Windows/Android, no cambia sus datos y no crea ni envía una APK.

## Alcance implementado de la base

- Registro propio del iPhone, sustitución del secreto del QR por credencial individual y almacenamiento preparado con Keychain `ThisDeviceOnly`.
- Identidad y selección de PC independientes de su nombre o IP; verificación de identidad antes de enviar credenciales a otra ruta.
- Explorador de carpetas y listado de archivos mediante las API existentes de Windows.
- Chat de texto con el motor de la PC, sesión explícita, actualización mientras está visible y acceso al último mensaje.
- Bloqueo de escrituras si el llavero no puede leerse; sin sobrescribir conexiones previas.
- Rechazo de redirecciones autenticadas y de destinos públicos no admitidos. El chat exige Tailscale o HTTPS.
- Cámara y Keychain Apple implementados en Swift y compilados por Xcode; la identidad local se utiliza en el inicio del simulador. Su vinculación y comportamiento físico siguen pendientes.

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

No se instalaron Xcode ni un simulador Apple en Windows. El propietario eligió AltStore gratuito. AltServer 1.8.01 se instaló en `C:\Desarrollo\Herramientas\Ayudix-iOS\AltServer`, con resultado Windows Installer 0, y su proceso está activo. Sus archivos exteriores no tienen firma Authenticode. iTunes 12.13.12.3 y Apple Mobile Device Support 20.0.1.47 también están instalados, y Windows detecta el iPhone. El instalador firmado de iCloud 7.21 falló con 1722; se preparó una carpeta alternativa de bibliotecas originales con `AOSKit.dll` firmado. AltServer tiene configurada su propia carpeta de bibliotecas, que ya contiene AOSKit y CoreFoundation; se conservó esa selección. El propietario informó que introdujo su cuenta Apple en AltServer. El agente no leyó ni introdujo contraseñas Apple; no hay credenciales Apple en las fuentes, los artefactos o Actions. La instalación efectiva de AltStore y de Ayudix en el teléfono aún requiere confirmación.

## Compilación Apple y repositorio

El repositorio local no tenía un remoto `origin`. El repositorio de GitHub encontrado para Ayudix es **Jeamlake/ayudix-releases**, público. No se añade por suposición un remoto al árbol local con cambios históricos pendientes ni se publica ese árbol completo.

La preparación se exporta con una lista explícita de fuentes nuevas y revisión de contenido. El workflow local está en `.github/workflows/ayudix-ios.yml`; para revisión sin activar Actions puede guardarse como `docs/ios/ayudix-ios.workflow.yml`. Esa copia es inerte y no concede permisos.

El propietario completó la autorización oficial de GitHub CLI con el alcance `workflow`. Se activó la compilación manual en `main`, que lee un commit concreto de la rama iOS sin fusionarla. Esta autorización pertenece a la herramienta de desarrollo y no amplía los permisos del conector GitHub de Ayudix.

La preparación permanece en el borrador https://github.com/Jeamlake/ayudix-releases/pull/1. La continuación de dispositivo es https://github.com/Jeamlake/ayudix-releases/actions/runs/41092884011, con fuentes `35408f086ecbf594af09cb8fe2786823c4bce059`. Su arranque no acredita todavía éxito o un IPA instalable.

## Pendiente para entregar al iPhone 17

1. Completar AltStore en el iPhone y firmar el IPA verificado mediante el canal personal elegido.
2. Validar orientación, fuentes, teclado, accesibilidad y permisos en el iPhone físico.
3. Renovar la firma personal cuando corresponda; el IPA original es **sin firma**, no instalable directamente.
4. Probar la vinculación y el comportamiento de cámara/red/Keychain en el teléfono físico. No hay un IPA firmado ni una instalación acreditada en esta preparación.
5. Incorporar transferencias verificadas, multimedia, voz y escritorio por etapas; no trasladar el módulo Android de streaming como si fuera binario iOS.

La [guía de instalación desde Windows](INSTALAR-DESDE-WINDOWS.md) explica la firma personal elegida con AltStore y la prueba física pendiente.
