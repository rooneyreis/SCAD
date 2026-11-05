# Aplica a migração da loja (db/init/05-loja.sh) aos fragmentos de uma instalação JÁ EXISTENTE,
# sem apagar dados. Numa instalação nova (docker compose down -v / up) não é preciso: o script
# corre sozinho na inicialização de cada PostgreSQL.
#
# Corre-se em cada PRIMÁRIO; as réplicas recebem as alterações pela streaming replication.
# É idempotente: pode ser executado várias vezes.
#
# Uso (na raiz do repositório):  powershell -ExecutionPolicy Bypass -File deploy\migrar-loja.ps1

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot 'local\docker-compose.yml'

foreach ($pg in 'pg-norte', 'pg-centro', 'pg-sul') {
    Write-Host "== $pg"
    docker compose -f $compose exec -T $pg bash /docker-entrypoint-initdb.d/05-loja.sh
    if ($LASTEXITCODE -ne 0) { throw "A migração falhou em $pg" }
}
Write-Host 'Migração concluída. Reconstrua os nós: docker compose -f deploy\local\docker-compose.yml up -d --build'
