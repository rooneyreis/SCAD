# SCAD — Sistema de Compras Automáticas Distribuídas

Projecto de Sistemas Distribuídos e Paralelos (ISUTC, LECC 31).

O SCAD vigia o stock de 3 armazéns regionais (NORTE, CENTRO, SUL) e cria automaticamente
ordens de compra consolidadas aos fornecedores. Continua a funcionar quando uma máquina cai.

> **Demonstração:** ver [`docs/guiao-demo.md`](docs/guiao-demo.md) (passo a passo e cenários de falha).
> Resumo de cada fase em [`docs/resumos/`](docs/resumos); decisões de design para o relatório em
> [`docs/notas-relatorio.md`](docs/notas-relatorio.md).
>
> A demo corre num só PC (`deploy/local`), com cada componente num container. O deploy em 3 máquinas
> está preparado em [`deploy/fumo/`](deploy/fumo/README.md) (teste de fumo com 2 máquinas), mas não foi executado.

## Estrutura

```
idl/SCAD.idl             interface CORBA (fonte única; os stubs Java são gerados no build)
scad-common/             ORB JacORB, Naming Service, relógios (Lamport, corrigido), cliente HDFS
scad-no/                 nó regional (Spring Boot): agente de stock CORBA, Ricart-Agrawala, 2PC,
                         failover, gateway de fornecedores, facturas PDF, API REST
scad-central/            Central de Auditoria CORBA, mestre Berkeley, auditoria periódica
fornecedor-contrato/     contrato do fornecedor (interface JAX-WS + mensagens JSON/XML)
fornecedor-mock/         fornecedor simulado: REST + SOAP (WSDL), interruptor de falhas
painel/                  painel React (Vite) + nginx (proxy para os nós)
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
| **Painéis** | **:8081 NORTE · :8082 CENTRO · :8083 SUL · :8080 vista geral** — `http://localhost:8081` ou `http://<IP do PC>:8081` a partir de outro portátil (`deploy/abrir-porta-painel.ps1`) |
| no-NORTE / no-CENTRO / no-SUL (API) | http://localhost:18081 · 18082 · 18083 |
| Fornecedor (REST / WSDL) | http://localhost:8090/api/v1/ordens · http://localhost:8090/ws/fornecedor?wsdl |
| UI do HDFS (NameNode) | http://localhost:9870 |

Para parar: `docker compose -f deploy/local/docker-compose.yml down`.
Com `-v` apaga também os dados (as bases de dados voltam ao stock inicial no próximo `up`).

## API do nó (principais)

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/global/stock`, `/api/global/movimentos` | todos os armazéns (lidos dos fragmentos) |
| GET / POST | `/api/ordens`, `/api/ordens/{id}/entrega` | ordens de compra; receber entrega (2PC) |
| GET | `/api/2pc`, `/api/fragmentos`, `/api/exclusao`, `/api/relogio` | monitor: 2PC, failover, Ricart-Agrawala, Berkeley |
| GET | `/api/ficheiros`, `/api/ficheiros/conteudo?caminho=` | HDFS |
| GET | `/api/estado` | região, relógios (corrigido, desvio, Lamport), Central e HDFS acessíveis |
| GET | `/api/stock` | stock do armazém do nó, com catálogo e nível (NORMAL / AVISO / CRITICO / RUPTURA) |
| POST | `/api/movimentos/saida` | `{"armazemId","produtoId","quantidade","motivo"}` → 201, 400, 404, 409, 422 |
| GET | `/api/movimentos?limite=50` | movimentos recentes (carimbo de Lamport + hora corrigida) |
| GET | `/api/alertas?limite=50` | alertas emitidos (`enviadoEm` nulo = ainda na fila local) |

Exemplo (PowerShell):

```powershell
$saida = @{ armazemId='NORTE'; produtoId='ARROZ-25'; quantidade=10; motivo='Venda' } | ConvertTo-Json
Invoke-RestMethod http://localhost:18081/api/movimentos/saida -Method Post -Body $saida -ContentType 'application/json; charset=utf-8'
docker compose -f deploy/local/docker-compose.yml logs central     # "ALERTA CRITICO de NORTE ..."
```

## Testes

Testes unitários e de integração (PostgreSQL real via Testcontainers), dentro de Docker:

```powershell
docker run --rm -v "${PWD}:/src" -v scad-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /src maven:3.9-eclipse-temurin-17 mvn -B verify
```
