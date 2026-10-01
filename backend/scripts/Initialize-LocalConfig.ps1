param([string]$AdminEmail = 'admin@telemicro.local')
$ErrorActionPreference = 'Stop'
$configPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env.local'))
if (Test-Path -LiteralPath $configPath) {
    throw '.env.local já existe. A configuração e as credenciais existentes foram preservadas.'
}
if ($AdminEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$' -or $AdminEmail.Length -gt 254) {
    throw 'Informe um e-mail válido para o administrador local.'
}
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $keyBytes = New-Object byte[] 32
    $passwordBytes = New-Object byte[] 24
    $random.GetBytes($keyBytes)
    $random.GetBytes($passwordBytes)
    $jwtSecret = [Convert]::ToBase64String($keyBytes)
    $localPassword = [Convert]::ToBase64String($passwordBytes)
} finally { $random.Dispose() }
@(
    '# Credenciais locais geradas aleatoriamente. Arquivo ignorado pelo Git.'
    '# Nunca compartilhar ou reutilizar em hospedagem.'
    "JWT_SECRET=$jwtSecret"
    'BOOTSTRAP_ENABLED=true'
    "BOOTSTRAP_ADMIN_EMAIL=$($AdminEmail.ToLowerInvariant())"
    "BOOTSTRAP_ADMIN_PASSWORD=$localPassword"
) | Set-Content -LiteralPath $configPath -Encoding utf8
Write-Output 'Configuração criada em backend/.env.local. Consulte o arquivo para obter a senha local.'
