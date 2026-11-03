<#
.SYNOPSIS
    Verifica o teste de fumo com 2 máquinas. Executar na M1, na pasta deploy\fumo\m1,
    depois de as duas máquinas estarem a correr há ~1 minuto.

.EXAMPLE
    cd deploy\fumo\m1
    powershell -ExecutionPolicy Bypass -File ..\verificar-fumo.ps1
#>
$ErrorActionPreference = 'Continue'

# Lê NORTE_IP e CENTRO_IP do .env desta pasta
$envVars = @{}
Get-Content .env | Where-Object { $_ -match '^\s*([A-Z_]+)=(.*)$' } |
    ForEach-Object { $envVars[$Matches[1]] = $Matches[2].Trim() }
$norte = $envVars['NORTE_IP']
$centro = $envVars['CENTRO_IP']
if (-not $norte -or -not $centro) { throw 'Defina NORTE_IP e CENTRO_IP no .env' }

$falhas = 0
function Resultado($nome, $ok, $detalhe) {
    if ($ok) { Write-Host "[OK]    $nome" -ForegroundColor Green }
    else { Write-Host "[FALHA] $nome" -ForegroundColor Red; $script:falhas++ }
    if ($detalhe) { Write-Host "        $detalhe" }
}

Write-Host "`n=== 1. HTTP entre máquinas ==="
foreach ($par in @(@('NORTE', $norte), @('CENTRO', $centro))) {
    try {
        $e = Invoke-RestMethod "http://$($par[1]):8080/api/estado" -TimeoutSec 10
        Resultado "no-$($par[0]) responde em $($par[1]):8080" $true "central=$($e.centralDisponivel) hdfs=$($e.hdfsDisponivel) desvio=$($e.desvioMs) ms"
        Resultado "no-$($par[0]) registado na Central (CORBA)" $e.centralDisponivel
        Resultado "no-$($par[0]) vê o HDFS" $e.hdfsDisponivel
    } catch {
        Resultado "no-$($par[0]) responde em $($par[1]):8080" $false $_.Exception.Message
    }
}

Write-Host "`n=== 2. CORBA: callback da Central (M1) ao agente da M2 ==="
$logs = docker compose logs --no-log-prefix central 2>&1 | Out-String
Resultado 'Central chamou de volta o agente CENTRO (IOR com o IP da M2)' ($logs -match 'Callback OK para CENTRO') `
    (($logs -split "`n" | Select-String 'Callback .* CENTRO' | Select-Object -Last 1) -as [string]).Trim()

Write-Host "`n=== 3. CORBA: alerta enviado pela M2 chega à Central ==="
$saida = @{ armazemId = 'CENTRO'; produtoId = 'FARINHA-25'; quantidade = 15; motivo = 'Teste de fumo' } | ConvertTo-Json
try {
    $r = Invoke-RestMethod "http://${centro}:8080/api/movimentos/saida" -Method Post -Body $saida -ContentType 'application/json; charset=utf-8'
    Start-Sleep -Seconds 4
    $id = $r.alerta.id
    $logs = docker compose logs --no-log-prefix central 2>&1 | Out-String
    Resultado "Saída no CENTRO gerou alerta $($r.alerta.nivel)" ($null -ne $id)
    Resultado 'Alerta chegou à Central na M1' ($id -and $logs.Contains($id))
} catch {
    Resultado 'Saída no CENTRO' $false $_.Exception.Message
}

Write-Host "`n=== 4. HDFS: escrita a partir das duas máquinas, replicação 2 ==="
foreach ($ip in @($norte, $centro)) {
    try {
        $h = Invoke-RestMethod "http://${ip}:8080/api/prova/hdfs" -Method Post -TimeoutSec 30
        Resultado "Escrita/leitura HDFS a partir de $ip" ($null -ne $h.lido) $h.caminho
    } catch {
        Resultado "Escrita/leitura HDFS a partir de $ip" $false $_.Exception.Message
    }
}
$relatorio = docker compose exec -T namenode hdfs dfsadmin -report 2>$null | Out-String
Resultado 'NameNode vê 2 DataNodes vivos' ($relatorio -match 'Live datanodes \(2\)') `
    ((($relatorio -split "`n") | Select-String '^Name:|^Hostname:') -join ' | ')
$fsck = docker compose exec -T namenode hdfs fsck /scad/prova -files -blocks -locations 2>$null | Out-String
Resultado 'Ficheiros com 2 réplicas (Live_repl=2)' ($fsck -match 'Live_repl=2' -and $fsck -notmatch 'Live_repl=1')

Write-Host "`n=== 5. PostgreSQL: replicação NORTE (M1) -> réplica (M2) ==="
$repl = docker compose exec -T pg-norte psql -U scad -d scad -tAc "SELECT client_addr || ' ' || state FROM pg_stat_replication" 2>&1 | Out-String
Resultado 'Primário NORTE tem uma réplica em streaming' ($repl -match 'streaming') $repl.Trim()
$marca = "fumo-$(Get-Date -Format 'HHmmss')"
docker compose exec -T pg-norte psql -U scad -d scad -c "CREATE TABLE IF NOT EXISTS teste_fumo(txt text); INSERT INTO teste_fumo VALUES ('$marca')" | Out-Null
Start-Sleep -Seconds 2
$naReplica = docker run --rm -e PGPASSWORD=$($envVars['PG_PASSWORD']) postgres:16 psql -h $centro -p 5433 -U scad -d scad -tAc "SELECT count(*) FROM teste_fumo WHERE txt = '$marca'" 2>&1 | Out-String
Resultado "Linha escrita na M1 aparece na réplica da M2 (${centro}:5433)" ($naReplica.Trim() -eq '1') $naReplica.Trim()

Write-Host ''
if ($falhas -eq 0) { Write-Host 'TESTE DE FUMO: TUDO OK' -ForegroundColor Green }
else { Write-Host "TESTE DE FUMO: $falhas FALHA(S)" -ForegroundColor Red }
