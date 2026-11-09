# syntax=docker/dockerfile:1
# ms-pedidos: build multi-stage (Maven + JDK 17) y runtime mínimo (solo JRE 17 y el jar ejecutable).

# ---- Etapa 1: compilar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Primero solo el pom: la descarga de dependencias queda en caché mientras no cambie.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Etapa 2: ejecutar ----
FROM eclipse-temurin:17-jre
# Usuario sin privilegios: si el proceso se compromete, no es root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/target/ms-pedidos.jar app.jar
USER app
# Configuración (BD, Cognito, RabbitMQ) solo por variables de entorno; ver .env.example en la raíz.
ENV PORT=8082
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
