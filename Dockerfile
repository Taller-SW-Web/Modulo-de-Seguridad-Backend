# Backend G7 (TEC-INFRA-T005). Imagen sin secretos: la clave de firma, la CA de la base,
# la lista de contraseñas comunes y las credenciales llegan en el despliegue como
# variables o archivos secretos (docs/despliegue/DESPLIEGUE-T005.md).

# --- Build: el jar se construye dentro de la imagen; las pruebas corren en el CI. ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /fuente
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/auth-service-*-SNAPSHOT.jar /fuente/app.jar

# --- Runtime: solo JRE, usuario sin privilegios. ---
FROM eclipse-temurin:21-jre
RUN groupadd --system g7 && useradd --system --gid g7 --home-dir /app --shell /usr/sbin/nologin g7 \
    && mkdir -p /app /run/config/g7 && chown -R g7:g7 /app
WORKDIR /app
COPY --from=build --chown=g7:g7 /fuente/app.jar app.jar
USER g7

ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
