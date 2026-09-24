# ── Etapa 1: compilar el jar ─────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Dependencias primero: esta capa queda en caché mientras no cambie el pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline

# Los tests no corren acá: necesitan una base Postgres
COPY src/ src/
RUN ./mvnw -q -DskipTests package && cp target/ordership-*.jar app.jar

# ── Etapa 2: imagen final, solo con el JRE y el jar ──────
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /build/app.jar app.jar
USER app

# Memoria acotada: el hosting cobra por RAM y sin límite la JVM reserva mucho más de lo que usa.
# Medido: ~335 MB en total con estos valores. Se pueden sobreescribir con la variable JAVA_OPTS.
ENV JAVA_OPTS="-Xmx256m -Xss512k -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
