param(
    [Parameter(Mandatory)][string]$BackupPath,
    [string]$DatabaseName = ('telemicro_restore_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
)
$ErrorActionPreference = 'Stop'
if ($DatabaseName -notmatch '^telemicro_restore_[a-z0-9_]+$') {
    throw 'Use um banco novo com prefixo telemicro_restore_. O banco principal não pode ser sobrescrito por este script.'
}
$sourcePath = (Resolve-Path -LiteralPath $BackupPath).Path
$composePath = Join-Path $PSScriptRoot '../compose.yaml'
$containerPath = '/tmp/telemicro-restore-' + [guid]::NewGuid().ToString('N') + '.dump'
try {
    & docker compose -f $composePath cp $sourcePath "postgres:$containerPath"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar backup.' }
    & docker compose -f $composePath exec -T postgres pg_restore --list $containerPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Arquivo de backup inválido.' }
    & docker compose -f $composePath exec -T postgres createdb -U telemicro $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível criar um banco novo. Bancos existentes não serão sobrescritos.' }
    & docker compose -f $composePath exec -T postgres pg_restore -U telemicro -d $DatabaseName --no-owner --no-privileges --single-transaction --exit-on-error $containerPath
    if ($LASTEXITCODE -ne 0) { throw "Falha na restauração. O banco de teste $DatabaseName foi mantido para inspeção." }
    Write-Output "Backup restaurado no banco separado $DatabaseName. O banco principal foi preservado."
} finally {
    & docker compose -f $composePath exec -T postgres rm -f $containerPath
}
