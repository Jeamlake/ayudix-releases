param(
    [Parameter(Mandatory=$true)][string]$ICloudMsi,
    [Parameter(Mandatory=$true)][string]$SevenZip,
    [string]$AppleSupport = 'C:\Program Files (x86)\Common Files\Apple\Apple Application Support',
    [string]$OutputRoot = 'C:\Desarrollo\Herramientas\Ayudix-iOS\DependenciasApple\7.21\Apple',
    [string]$ExtractionRoot = 'C:\Desarrollo\Cache\Ayudix-iOS\AppleLibraries\7.21',
    [string]$Receipt = 'C:\Desarrollo\Logs\Ayudix\iOS-Preparacion-2026-10-30\apple-library-preparation.json'
)
$ErrorActionPreference = 'Stop'
# Extract vendor files as data. Do not modify an MSI, run its custom actions,
# register iCloud, replace system libraries or redistribute Apple's binaries.
$signature = Get-AuthenticodeSignature -LiteralPath $ICloudMsi
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Apple Inc\.') {
    throw 'Se necesita el MSI original con firma válida de Apple.'
}
if (-not (Test-Path -LiteralPath $AppleSupport -PathType Container)) { throw 'Falta Apple Application Support instalado por Apple.' }
if ((Test-Path -LiteralPath $OutputRoot) -and (Get-ChildItem -LiteralPath $OutputRoot -Force | Select-Object -First 1)) { throw 'La carpeta de salida ya contiene archivos. Revísala antes de preparar otra copia.' }
New-Item -ItemType Directory -Path $ExtractionRoot -Force | Out-Null
& $SevenZip x -y -bb0 -bso0 -bsp0 $ICloudMsi 'iCloud.cab' ('-o' + $ExtractionRoot)
if ($LASTEXITCODE -ne 0) { throw 'No se pudo extraer el CAB original.' }
$rawFiles = Join-Path $ExtractionRoot 'files'
& $SevenZip x -y -bb0 -bso0 -bsp0 (Join-Path $ExtractionRoot 'iCloud.cab') ('-o' + $rawFiles)
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron leer las bibliotecas del CAB.' }
$engine = New-Object -ComObject WindowsInstaller.Installer
$database = $engine.OpenDatabase((Resolve-Path -LiteralPath $ICloudMsi).Path, 0)
$directories = @{}; $components = @{}
$view = $database.OpenView('SELECT Directory,Directory_Parent,DefaultDir FROM Directory'); $view.Execute()
while ($row = $view.Fetch()) { $directories[$row.StringData(1)] = @{parent=$row.StringData(2);name=$row.StringData(3)} }
$view.Close()
$view = $database.OpenView('SELECT Component,Directory_ FROM Component'); $view.Execute()
while ($row = $view.Fetch()) { $components[$row.StringData(1)] = $row.StringData(2) }
$view.Close()
$output = [IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Path $output -Force | Out-Null
$count = 0
$view = $database.OpenView('SELECT File,FileName,Component_ FROM File'); $view.Execute()
while ($row = $view.Fetch()) {
    $directory = $components[$row.StringData(3)]; $parts = [Collections.Generic.List[string]]::new(); $isInternetService = $false
    $seen = [Collections.Generic.HashSet[string]]::new()
    while ($directory -and $seen.Add($directory)) {
        $info = $directories[$directory]
        $name = ($info.name.Split(':')[0].Split('|') | Select-Object -Last 1)
        if ($name -ne '.') { $parts.Insert(0, $name) }
        if ($directory -eq 'InternetServicesFolder') { $isInternetService = $true; break }
        $directory = $info.parent
    }
    if (-not $isInternetService) { continue }
    $fileName = ($row.StringData(2).Split('|') | Select-Object -Last 1)
    if ($fileName -match '[/\\]' -or $fileName -in @('.','..')) { throw 'Nombre inesperado en el MSI.' }
    $segments = @($parts) + @($fileName)
    $relative = [IO.Path]::Combine([string[]]$segments)
    $target = [IO.Path]::GetFullPath((Join-Path $output $relative))
    if (-not $target.StartsWith($output + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Ruta del MSI fuera de la carpeta de herramientas.' }
    $source = Join-Path $rawFiles $row.StringData(1)
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw ('Falta un archivo original del CAB: ' + $row.StringData(1)) }
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
    $count++
}
$view.Close()
# This Program Files folder contains installed library code, not an Apple user profile.
$supportFiles = @(Get-ChildItem -LiteralPath $AppleSupport -Recurse -File)
foreach ($file in $supportFiles) {
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint -or $file.Extension -match '^\.(db|sqlite|pem|p12|key)$') { throw 'Contenido inesperado en la carpeta de bibliotecas.' }
    $relative = [IO.Path]::GetRelativePath($AppleSupport, $file.FullName)
    $target = Join-Path (Join-Path $output 'Apple Application Support') $relative
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
$aosKit = Join-Path $output 'Internet Services\AOSKit.dll'
$aosSignature = Get-AuthenticodeSignature -LiteralPath $aosKit
if ($aosSignature.Status -ne 'Valid' -or $aosSignature.SignerCertificate.Subject -notmatch 'O=Apple Inc\.') { throw 'La biblioteca AOSKit extraída no pasó la firma de Apple.' }
$result = [ordered]@{
    ok=$true;folder=$output;internetServicesFiles=$count;supportFiles=$supportFiles.Count
    originalMsiSha256=(Get-FileHash -LiteralPath $ICloudMsi -Algorithm SHA256).Hash
    aosKitSha256=(Get-FileHash -LiteralPath $aosKit -Algorithm SHA256).Hash
    aosKitSignature=$aosSignature.Status.ToString();iCloudInstalled=$false;systemLibrariesReplaced=$false;appleCredentialsRead=$false
    pending='Seleccionar esta carpeta en AltServer y comprobar su carga; todavía no se acredita la autenticación ni la instalación del iPhone.'
}
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($Receipt)) -Force | Out-Null
$result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $Receipt -Encoding utf8
$result | ConvertTo-Json -Compress
