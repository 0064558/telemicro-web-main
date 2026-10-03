param([switch]$UseJar)
$ErrorActionPreference = 'Stop'
$backendPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$configPath = Join-Path $backendPath '.env.local'
if (-not (Test-Path -LiteralPath $configPath)) {
    & (Join-Path $PSScriptRoot 'Initialize-LocalConfig.ps1')
}
& docker compose -f (Join-Path $backendPath 'compose.yaml') up -d --wait postgres
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível preparar o banco. Confira se o Docker Desktop está aberto e a porta 15432 está disponível.' }
$allowedKeys = @('JWT_SECRET', 'BOOTSTRAP_ENABLED', 'BOOTSTRAP_ADMIN_EMAIL', 'BOOTSTRAP_ADMIN_PASSWORD',
    'BOOTSTRAP_DEMO_EMAIL', 'BOOTSTRAP_DEMO_PASSWORD', 'DEMO_ENABLED', 'CORS_ALLOWED_ORIGINS')
$previous = @{}
try {
    foreach ($line in Get-Content -LiteralPath $configPath) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        $parts = $line.Split(@('='), 2)
        if ($parts.Length -ne 2 -or $allowedKeys -notcontains $parts[0]) {
            throw 'Configuração local inválida: use somente as variáveis documentadas, sem comandos.'
        }
        $keyName = $parts[0]
        if (-not $previous.ContainsKey($keyName)) {
            $previous[$keyName] = [Environment]::GetEnvironmentVariable($keyName, 'Process')
        }
        [Environment]::SetEnvironmentVariable($keyName, $parts[1], 'Process')
    }
    Push-Location $backendPath
    try {
        if ($UseJar) {
            & java -jar target/telemicro-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
        } else {
            & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
        }
        if ($LASTEXITCODE -ne 0) { throw "A API encerrou com código $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    foreach ($keyName in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($keyName, $previous[$keyName], 'Process')
    }
}
