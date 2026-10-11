param(
    [Parameter(Mandatory = $true)]
    [string]$ApiUrl
)
$ErrorActionPreference = 'Stop'
$apiBase = $null
if (-not [Uri]::TryCreate($ApiUrl.TrimEnd('/'), [UriKind]::Absolute, [ref]$apiBase) -or
    $apiBase.Scheme -ne 'https' -or $apiBase.AbsolutePath -ne '/api/v1' -or
    $apiBase.UserInfo -or $apiBase.Query -or $apiBase.Fragment) {
    throw 'Informe a URL HTTPS publica da API terminando em /api/v1, sem credenciais ou parametros.'
}
$projectPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$buildPath = Join-Path $projectPath 'dist/telemicro-web/browser'
$releasePath = Join-Path $projectPath 'release'
Push-Location $projectPath
try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'O build do site falhou.' }
    $configJson = @{ apiUrl = $apiBase.AbsoluteUri } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText((Join-Path $buildPath 'assets/config/api-config.js'),
        "window.telemicroConfig = $configJson;", [Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '.htaccess') -Destination $buildPath
    New-Item -ItemType Directory -Path $releasePath -Force | Out-Null
    $zipPath = Join-Path $releasePath ('telemicro-hostgator-' + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '.zip')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($buildPath, $zipPath)
    Write-Output "Pacote criado: $zipPath"
} finally { Pop-Location }
