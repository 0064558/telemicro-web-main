$ErrorActionPreference = 'Stop'
$configPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../.env.local'))
if (-not (Test-Path -LiteralPath $configPath)) {
    & (Join-Path $PSScriptRoot 'Initialize-LocalConfig.ps1')
}
$securePassword = Read-Host 'Senha de aplicativo do Gmail remetente (entrada oculta)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $mailPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) -replace '\s', ''
    if ($mailPassword -notmatch '^[a-zA-Z]{16}$') { throw 'Informe os 16 caracteres da senha de aplicativo gerada pelo Google.' }
    $existing = @(Get-Content -LiteralPath $configPath | Where-Object { $_ -notmatch '^MAIL_(ENABLED|HOST|PORT|USERNAME|PASSWORD|FROM|TO|PANEL_URL)=' })
    $existing + @(
        'MAIL_ENABLED=true'
        'MAIL_HOST=smtp.gmail.com'
        'MAIL_PORT=587'
        'MAIL_USERNAME=rodrigogggg12@gmail.com'
        "MAIL_PASSWORD=$mailPassword"
        'MAIL_FROM=Telemicro - Notificacoes <rodrigogggg12@gmail.com>'
        'MAIL_TO=rodrigodois9@gmail.com'
        'MAIL_PANEL_URL=http://localhost:4200/admin'
    ) | Set-Content -LiteralPath $configPath -Encoding utf8
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $mailPassword = $null
}
Write-Output 'Envio configurado em .env.local (ignorado pelo Git). Reinicie a API pelo Start-Local.ps1.'
