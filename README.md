# SCAD — Sistema de Compras Automáticas Distribuídas

Projecto de Sistemas Distribuídos e Paralelos (ISUTC, LECC 31).

O SCAD vigia o stock de 3 armazéns regionais (NORTE, CENTRO, SUL) e cria automaticamente
ordens de compra consolidadas aos fornecedores. Continua a funcionar quando uma máquina cai.

> Estado: **Fase 2** (dados, saídas de stock, alertas CORBA). Teste de fumo com 2 máquinas em
> [`deploy/fumo/`](deploy/fumo/README.md). As instruções completas de deploy em 3 máquinas e os
> cenários de falha chegam nas Fases 8 e 9.

## Estrutura

```
idl/SCAD.idl             interface CORBA (fonte única; os stubs Java são gerados no build)
scad-common/             ORB JacORB, Naming Service, relógios (Lamport, corrigido), cliente HDFS
scad-no/                 nó regional (Spring Boot): agente de stock CORBA, API REST
scad-central/            Central de Auditoria CORBA
db/init/                 esquema, catálogo e fragmentação (correm no 1.º arranque de cada primário)
db/dados/                stock inicial de cada região
db/replicacao/           arranque das réplicas (streaming replication)
deploy/local/            docker-compose com tudo num só PC
deploy/fumo/             teste de fumo com 2 máquinas
deploy/abrir-portas.ps1  regras da Firewall do Windows
docs/                    notas para o relatório técnico
```

## Requisitos

- Docker Desktop (com o motor Linux). Não é preciso JDK nem Maven: tudo compila dentro de Docker.
- Modo local: cerca de 3 GB de RAM livres para o Docker.

## Arrancar em modo local

```powershell
docker compose -f deploy/local/docker-compose.yml up -d --build
docker compose -f deploy/local/docker-compose.yml ps
```

| Serviço | Endereço |
|---|---|
| no-NORTE / no-CENTRO / no-SUL | http://localhost:8081 · 8082 · 8083 |
| UI do HDFS (NameNode) | http://localhost:9870 |

Para parar: `docker compose -f deploy/local/docker-compose.yml down`.
Com `-v` apaga também os dados (as bases de dados voltam ao stock inicial no próximo `up`).

## API do nó

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/estado` | região, relógios (corrigido, desvio, Lamport), Central e HDFS acessíveis |
| GET | `/api/stock` | stock do armazém do nó, com catálogo e nível (NORMAL / AVISO / CRITICO / RUPTURA) |
| POST | `/api/movimentos/saida` | `{"armazemId","produtoId","quantidade","motivo"}` → 201, 400, 404, 409, 422 |
| GET | `/api/movimentos?limite=50` | movimentos recentes (carimbo de Lamport + hora corrigida) |
| GET | `/api/alertas?limite=50` | alertas emitidos (`enviadoEm` nulo = ainda na fila local) |

Exemplo (PowerShell):

```powershell
$saida = @{ armazemId='NORTE'; produtoId='ARROZ-25'; quantidade=10; motivo='Venda' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/api/movimentos/saida -Method Post -Body $saida -ContentType 'application/json; charset=utf-8'
docker compose -f deploy/local/docker-compose.yml logs central     # "ALERTA CRITICO de NORTE ..."
```

## Testes

Testes unitários e de integração (PostgreSQL real via Testcontainers), dentro de Docker:

```powershell
docker run --rm -v "${PWD}:/src" -v scad-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /src maven:3.9-eclipse-temurin-17 mvn -B verify
```
