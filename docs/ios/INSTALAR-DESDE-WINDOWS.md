# Primera instalación en el iPhone desde Windows

Esta guía acompaña la preparación 0.1.0. El paquete terminado en `UNSIGNED.ipa` necesita firma antes de abrirse en el teléfono. El ZIP de fuentes no se instala. La compilación y sus pruebas se registran por separado en [el estado de la entrega](ESTADO-PREPARACION-2026-10-30.md).

## Pruebas personales gratuitas

El propietario eligió **AltStore Classic con AltServer en Windows**. Requiere tu cuenta Apple y renovación de firma cada siete días; no es una publicación en App Store. La primera instalación requiere tu intervención. No se compró una membresía ni se añadió tu cuenta Apple a GitHub.

1. Usa la [guía oficial de AltStore para Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) para instalar sus dependencias Apple y AltServer. Si ya usas las aplicaciones Apple de Microsoft Store, consulta la alternativa oficial antes de cambiar esa instalación.
2. Conecta el iPhone desbloqueado por USB y acepta su relación de confianza con esta PC. Selecciona ese iPhone al instalar AltStore; no el HONOR.
3. Introduce tú la cuenta Apple en el asistente de instalación. Nunca pegues contraseñas, códigos, certificados ni perfiles en este chat, en el repositorio o en un registro de Ayudix.
4. Completa la confianza del perfil y el modo desarrollador que solicite iOS. El modo desarrollador puede requerir reiniciar el teléfono.
5. Cuando exista un IPA nativo verificado, guárdalo en Archivos del iPhone e impórtalo desde AltStore. Que termine la descarga no demuestra que esté firmado o instalado.
6. Renueva la firma desde AltStore antes de su vencimiento, con AltServer disponible. Conserva el mismo canal de firma para actualizar encima y verifica que permanezcan las conexiones.

La PC mantiene instaladores en `C:\Desarrollo\Instaladores\Ayudix-iOS`, herramientas en `C:\Desarrollo\Herramientas\Ayudix-iOS` y resultados de compilación en `C:\Desarrollo\Logs\Ayudix\iOS-Preparacion-2026-10-30`. Esta guía no instala herramientas automáticamente.

## Si falla el instalador antiguo de iCloud

En esta PC se observó el error Windows Installer 1722 en `RuniCloudUpgrade` (`iCloud.exe /upgrade`). El fallo no acredita que iCloud se haya instalado. La conectividad HTTPS básica respondió y el reloj coincidió con Apple; esas comprobaciones no prueban un inicio de sesión correcto.

Para proporcionar las dependencias a AltServer se extrajeron los archivos originales de `iCloud64.msi`, cuya firma de Apple se verificó, y se copiaron las bibliotecas públicas de Apple Application Support ya instalado. No se modificó el MSI ni se ejecutaron sus acciones de instalación; tampoco se reemplazaron bibliotecas del sistema o se leyeron credenciales Apple. `AOSKit.dll` conserva su firma válida.

La carpeta preparada es `C:\Desarrollo\Herramientas\Ayudix-iOS\DependenciasApple\7.21\Apple`. Si AltServer muestra **iCloud Not Found**, elige **Choose Folder…** y selecciona esa carpeta, que contiene `Internet Services` y `Apple Application Support`. El uso de una carpeta separada de bibliotecas está descrito en la [guía oficial de AltStore](https://faq.altstore.io/altstore-classic/troubleshooting-guide).

El script reproducible es `scripts/ios/prepare-altstore-libraries.ps1`; requiere el MSI oficial con firma válida y una copia completa de 7-Zip. Los binarios de Apple permanecen fuera del repositorio y del ZIP GPL de fuentes. El recibo local distingue extracción, carga por AltServer y autenticación: estas dos últimas siguen pendientes hasta probarlas. Esto prepara una alternativa para AltServer; no repara la aplicación completa de iCloud ni demuestra que se haya resuelto el error de inicio de sesión de iTunes.

## Prueba física pendiente

Abre Ayudix y vincula el iPhone con su propio QR desde la seguridad de Windows. Comprueba la cámara, el permiso de red local, la selección de PC, las carpetas y el chat de texto. Para el chat, prepara Tailscale o el HTTPS admitido por el cliente. No reutilices la credencial del HONOR ni publiques el QR.

La preparación lista archivos; aún no implementa transferencias, reproducción, voz ni escritorio remoto. No debe presentarse como equivalente funcional a la APK actual.

## Opción TestFlight

Si eliges TestFlight, primero hay que configurar Apple Developer, identificador, firma y App Store Connect. No se cobra ni se activa ese servicio por suposición. No se debe publicar el certificado o su clave privada en las fuentes.

## Fuentes

- [Instalación oficial de AltStore Classic en Windows](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows).
- [Renovación y límites de AltStore Classic](https://faq.altstore.io/altstore-classic/your-altstore).
- [Modo desarrollador de Apple](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device).
- [TestFlight de Apple](https://developer.apple.com/testflight/).

Consulta realizada el 30 de octubre de 2026. Comprueba los requisitos actuales del instalador antes de la instalación física.
