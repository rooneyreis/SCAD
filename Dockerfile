# syntax=docker/dockerfile:1
# Build multi-stage: compila tudo com Maven + JDK 17 e gera uma imagem por serviço (target).
#   docker build --target no      -> nó regional
#   docker build --target central -> Central de Auditoria
#   docker build --target naming  -> CORBA Naming Service (JacORB)

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
# A cache do ~/.m2 é partilhada entre builds (não é preciso voltar a descarregar dependências)
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre AS base
WORKDIR /app
ENV JAVA_OPTS="-Xms128m -Xmx512m"

FROM base AS no
COPY --from=build /src/scad-no/target/lib/ lib/
COPY --from=build /src/scad-no/target/app.jar app.jar
EXPOSE 8080 3701
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp 'app.jar:lib/*' mz.ac.isutc.scad.no.AplicacaoNo"]

FROM base AS central
COPY --from=build /src/scad-central/target/lib/ lib/
COPY --from=build /src/scad-central/target/app.jar app.jar
EXPOSE 3700
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp 'app.jar:lib/*' mz.ac.isutc.scad.central.AplicacaoCentral"]

FROM base AS fornecedor
COPY --from=build /src/fornecedor-mock/target/lib/ lib/
COPY --from=build /src/fornecedor-mock/target/app.jar app.jar
EXPOSE 8090
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp 'app.jar:lib/*' mz.ac.isutc.scad.fornecedor.mock.AplicacaoFornecedor"]

# Naming Service do JacORB, numa porta fixa e com o endereço anunciado configurável.
# db_dir torna os registos persistentes entre reinícios.
FROM base AS naming
COPY --from=build /src/scad-central/target/lib/ lib/
EXPOSE 2809
ENV NAMING_OPTS="-Dorg.omg.CORBA.ORBClass=org.jacorb.orb.ORB -Dorg.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton -DOAPort=2809 -Djacorb.ior_proxy_port=2809 -Djacorb.naming.db_dir=/dados -Djavax.rmi.CORBA.UtilClass=mz.ac.isutc.scad.common.corba.rmi.UtilDelegateMinimo -Djavax.rmi.CORBA.StubClass=mz.ac.isutc.scad.common.corba.rmi.StubDelegateMinimo"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $NAMING_OPTS -Djacorb.ior_proxy_host=${CORBA_HOST_ANUNCIADO:?defina CORBA_HOST_ANUNCIADO} -cp 'lib/*' org.jacorb.naming.NameServer"]
