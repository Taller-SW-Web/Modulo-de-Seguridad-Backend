# Backend del proveedor de identidad — G7

Repositorio de código independiente de `Modulo-de-Seguridad`, que conserva las
fuentes SDD. Base nueva: Java 21, Spring Boot **3.5.16**, Gradle wrapper
**8.14.3**, paquete `com.marketplace.auth_service`. El pom Maven antiguo no se usa.

## Antes de implementar

Abrir también el clon de [documentación G7](https://github.com/Taller-SW-Web/Modulo-de-Seguridad).
Leer allí `AGENTS.md`, `plan/hito-3/BACKEND-HEXAGONAL.md`, el PLAN.md y
TAREAS.md propios. Esa revisión fija la estructura y los criterios de aceptación;
registrar su SHA junto al SHA del código. No copiar las specs como otra fuente editable.
En la disposición actual, la documentación es la carpeta padre; en otro equipo
puede ser un clon vecino. Las rutas de código en los tableros son relativas a
este repositorio, sin prefijo `backend/`.

Por capacidad: `domain`, `application/port/in`, `application/port/out`,
`application/dto`, `application/service`, `adapter/in/web`,
`adapter/in/transaction` y `adapter/out/persistence`. El núcleo no importa
Spring/JPA/HTTP. Entidades JPA, repositorios Spring Data y mappers manuales viven
en persistencia. Los otros dueños se consumen por puertos y DTO exportados.
No generar clases vacías de todas las funcionalidades en este arranque.

## Build y pruebas

```bash
./gradlew compileJava compileTestJava
./gradlew test
./gradlew bootJar
./gradlew dependencies --configuration runtimeClasspath
```

En Windows usar `gradlew.bat`. JAVA_HOME debe apuntar a JDK 21.
Testcontainers requiere Docker y utiliza PostgreSQL 16.6 y RabbitMQ
3.13.7-management. No sustituir estas pruebas por conexiones a Supabase.
El build inicial compila el scaffold; no demuestra funcionalidades H3 terminadas.

## Configuración pendiente de implementar

La conexión se hace por JDBC/PostgreSQL y JPA dentro de adaptadores.
DB_JDBC_URL, DB_USERNAME y DB_PASSWORD se suministran como secretos del entorno;
TLS verify-full, validación JPA y sin open-in-view. Runtime tiene Flyway apagado;
migración revisada con identidad/perfil separados, sin baseline automático.
Identificar esquema e historial Supabase antes de aplicar cambios. No heredar
contraseñas, entidades, migraciones ejecutables o contenedores del backend antiguo.
Las reservas de migración H3 están en el diseño SDD, no aplicadas a la base.

## Variables de entorno y perfiles (TEC-INFRA-T001)

| Variable | Uso |
|---|---|
| `DB_JDBC_URL` | JDBC del runtime, con `sslmode=verify-full`. Host/usuario de Connect del proyecto G7 real |
| `DB_USERNAME` / `DB_PASSWORD` | Credencial runtime (`g7_runtime`), sin privilegios de migración ni borrado |
| `DB_MIGRATION_JDBC_URL` / `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | Credencial migradora, solo para el perfil `migration` |
| `SPRING_PROFILES_ACTIVE` | `migration` para migrar; vacío por defecto en runtime |

Ver `.env.example`. El archivo `.env` está ignorado por Git.

```bash
# Levantar backend (runtime, sin migrar)
.\gradlew.bat bootRun
# Ejecutar migraciones revisadas (paso controlado, identidad migradora)
$env:SPRING_PROFILES_ACTIVE='migration'; .\gradlew.bat bootRun
```

Hikari fijo: máx 5, idle 1, timeout 10 s, vida 10 min, keepalive 2 min. Una
instancia H3, sin autoscaling.

Estado: scaffold y dependencias; casos de uso, configuración operativa y
recorridos funcionales siguen pendientes en los tableros de cada dueño.
