param([string]$Image = 'telemicro-api:render-ready')
$ErrorActionPreference = 'Stop'
$testPrefix = 'telemicro-deploy-test-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$testNetwork = "$testPrefix-network"
$testDatabase = "$testPrefix-db"
$testApi = "$testPrefix-api"
$testDatabasePassword = 'temporary-deployment-test-only'
$testKeyBytes = New-Object byte[] 32
$testRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $testRandom.GetBytes($testKeyBytes) } finally { $testRandom.Dispose() }
$testJwtKey = [Convert]::ToBase64String($testKeyBytes)
$networkCreated = $false
$databaseCreated = $false
$apiCreated = $false
try {
    & docker network create $testNetwork | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar rede temporaria.' }
    $networkCreated = $true
    & docker run -d --name $testDatabase --network $testNetwork `
        -e POSTGRES_DB=telemicro_test -e POSTGRES_USER=postgres `
        -e "POSTGRES_PASSWORD=$testDatabasePassword" postgres:17.9-bookworm | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar banco temporario.' }
    $databaseCreated = $true
    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $testDatabase pg_isready -U postgres -d telemicro_test 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $databaseReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $databaseReady) { throw 'O banco temporario nao iniciou.' }
    & docker run -d --name $testApi --network $testNetwork --memory 512m --cpus 1 `
        --publish '127.0.0.1::10000' `
        -e "DB_URL=jdbc:postgresql://${testDatabase}:5432/telemicro_test" `
        -e DB_USERNAME=postgres -e "DB_PASSWORD=$testDatabasePassword" -e "JWT_SECRET=$testJwtKey" `
        -e MAIL_ENABLED=false -e BOOTSTRAP_ENABLED=false -e DEMO_ENABLED=false $Image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar API temporaria.' }
    $apiCreated = $true
    $binding = & docker port $testApi 10000/tcp
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao obter porta de teste.' }
    $apiBase = 'http://' + ($binding | Select-Object -First 1)
    $healthReady = $false
    $serverErrors = 0
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod "$apiBase/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { $healthReady = $true; break }
        } catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 500) {
                $serverErrors++
                if ($serverErrors -ge 3) { break }
            }
        }
        $running = & docker inspect --format '{{.State.Running}}' $testApi
        if ($running -ne 'true') { break }
        Start-Sleep -Seconds 2
    }
    if (-not $healthReady) {
        $diagnosticPath = Join-Path $PSScriptRoot '../backend/target/render-smoke.log'
        & docker logs $testApi *> $diagnosticPath
        Get-Content -LiteralPath $diagnosticPath | Select-String -Pattern 'ERROR|Exception|Caused by|Started |OutOfMemory' | Select-Object -First 15
        throw 'A API nao ficou saudavel com o limite de 512 MB.'
    }
    $services = Invoke-RestMethod "$apiBase/api/v1/services" -TimeoutSec 10
    if ($services.Count -ne 7) { throw 'O catalogo inicial nao retornou os sete servicos esperados.' }
    Write-Output "Health UP; catalogo com $($services.Count) servicos; Java 21; limite de 512 MB."
    & docker stats --no-stream --format '{{.MemUsage}}' $testApi
} finally {
    # Os nomes foram gerados exclusivamente para este teste, sem volumes locais.
    if ($apiCreated) { & docker rm -f $testApi | Out-Null }
    if ($databaseCreated) { & docker rm -f $testDatabase | Out-Null }
    if ($networkCreated) { & docker network rm $testNetwork | Out-Null }
}
