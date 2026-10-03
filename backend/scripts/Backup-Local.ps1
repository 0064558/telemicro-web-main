param([string]$OutputDirectory = (Join-Path $PSScriptRoot '../backups'))
$ErrorActionPreference = 'Stop'
$composePath = Join-Path $PSScriptRoot '../compose.yaml'
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$backupPath = Join-Path $outputPath ("telemicro-{0}-{1}.dump" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), ([guid]::NewGuid().ToString('N').Substring(0,8)))
$containerPath = '/tmp/telemicro-backup-' + [guid]::NewGuid().ToString('N') + '.dump'
try {
    & docker compose -f $composePath exec -T postgres pg_dump -U telemicro -d telemicro -Fc -f $containerPath
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar backup.' }
    & docker compose -f $composePath cp "postgres:$containerPath" $backupPath
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar backup.' }
    Write-Output "Backup salvo em $backupPath"
} finally {
    & docker compose -f $composePath exec -T postgres rm -f $containerPath
}
