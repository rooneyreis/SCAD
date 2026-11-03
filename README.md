# SCAD — Sistema de Compras Automáticas Distribuídas

Projecto de Sistemas Distribuídos e Paralelos (ISUTC, LECC 31).

O SCAD vigia o stock de 3 armazéns regionais (NORTE, CENTRO, SUL) e cria automaticamente
ordens de compra consolidadas aos fornecedores. Continua a funcionar quando uma máquina cai.

> Estado: **Fase 1** (esqueleto e riscos técnicos). As instruções completas de deploy em
> 3 máquinas e os cenários de falha chegam nas Fases 8 e 9.

## Estrutura

```
idl/SCAD.idl        interface CORBA (fonte única; os stubs Java são gerados no build)
scad-common/        ORB JacORB, Naming Service, cliente HDFS, compatibilidade Java 17
scad-no/            nó regional (Spring Boot): agente de stock CORBA, API REST
scad-central/       Central de Auditoria CORBA
db/                 scripts PostgreSQL (replicação)
deploy/local/       docker-compose com tudo num só PC
docs/               notas para o relatório técnico
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

Para parar: `docker compose -f deploy/local/docker-compose.yml down` (com `-v` apaga também os dados).

## Testes

Testes unitários (dentro de Docker):

```powershell
docker run --rm -v "${PWD}:/src" -v scad-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-17 mvn -B test
```

Provas da Fase 1 com a stack a correr:

```powershell
curl.exe http://localhost:8081/api/estado                 # Central e HDFS acessíveis?
curl.exe -X POST http://localhost:8082/api/prova/alerta   # alerta CORBA -> ver "docker compose logs central"
curl.exe -X POST http://localhost:8083/api/prova/hdfs     # escreve e lê um ficheiro no HDFS
docker compose -f deploy/local/docker-compose.yml exec pg-norte psql -U scad -c "select * from pg_stat_replication"
```
