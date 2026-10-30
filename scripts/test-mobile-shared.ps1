[CmdletBinding()]
param([switch]$CoreOnly)
$ErrorActionPreference='Stop'
$taskRepo=Split-Path -Parent $PSScriptRoot
$taskGradle='C:\Desarrollo\Herramientas\Gradle\gradle-9.4.1\bin\gradle.bat'
$taskJava='C:\Desarrollo\Herramientas\Java\jdk-21'
if(!(Test-Path -LiteralPath $taskGradle) -or !(Test-Path -LiteralPath (Join-Path $taskJava 'bin\java.exe'))){throw 'Faltan Gradle 9.4.1 o JDK 21 en C:\Desarrollo\Herramientas. No se descargó nada en otras ubicaciones.'}
$env:JAVA_HOME=$taskJava
$env:GRADLE_USER_HOME='C:\Desarrollo\Cache\Gradle'
$env:KONAN_DATA_DIR='C:\Desarrollo\Cache\KotlinNative\ayudix-link'
$env:KOTLIN_DATA_DIR='C:\Desarrollo\Cache\Kotlin\ayudix-link'
$taskTasks=@(':core:jvmTest')
if(!$CoreOnly){$taskTasks+=@(':ui:jvmTest')}
& $taskGradle --project-dir (Join-Path $taskRepo 'mobile-shared') @taskTasks --max-workers=2 --no-daemon
if($LASTEXITCODE -ne 0){throw 'No pasó la verificación del código móvil compartido.'}
