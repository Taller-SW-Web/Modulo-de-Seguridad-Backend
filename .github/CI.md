# CI y protección del backend

Cada tarea sale de `develop` hacia una rama propia y abre PR hacia `develop`.
Las versiones verificadas llegan a `main` mediante PR de `develop` del mismo
repositorio. El check de destino impide aprobar con CI un PR de tarea a `main`.

El workflow ejecuta Java 21, Gradle wrapper, compilación, todas las pruebas,
reglas ArchUnit y empaquetado. Testcontainers usa Docker del runner con sus
PostgreSQL/RabbitMQ de prueba; no usa secretos ni se conecta a Supabase de nube.
Los reportes se conservan siete días, incluso si falla la ejecución.
No se excluyen pruebas para que el CI quede verde. Una base de prueba todavía
vacía no acredita que las migraciones o los recorridos funcionales estén listos.

## Protecciones que debe activar un administrador

Después de ejecutar este workflow, en Settings → Branches crear una regla para
`develop` y otra para `main`, con:

- Require a pull request before merging; una aprobación obligatoria.
- Dismiss stale pull request approvals when new commits are pushed.
- Require approval of the most recent reviewable push.
- Require status checks to pass y branches to be up to date before merging.
- Checks obligatorios: `Backend - destino del PR` y `Backend - build y pruebas`.
- Require conversation resolution before merging.
- Do not allow bypassing the above settings.
- Allow force pushes y Allow deletions desactivados.

Las protecciones son configuración de GitHub: este archivo no las activa.
Las ramas `main` y `develop` se conservan. Las ramas de tarea pueden eliminarse
después del merge. Los conflictos los resuelve el autor y el CI se vuelve a
ejecutar con la rama actualizada. Nadie aprueba su propio PR.

## Comprobar localmente

Con Java 21 y Docker funcionando:

```bash
bash ./gradlew --no-daemon --console=plain clean build
```

Para comprobar solo arquitectura, sin Docker:

```bash
bash ./gradlew --no-daemon test --tests '*ArquitecturaHexagonalTest' --tests '*ContratosExportadosRegresionTest'
```

Las reglas permiten paquetes aún sin clases en el scaffold y se aplican cuando
se implementan. Comprueban dependencias de clases, no el SQL ni la propiedad de
tablas por sí solos; esa parte también requiere revisión y pruebas funcionales.

## Contratos internos exportados

El check existente `Backend - build y pruebas` ejecuta también las reglas de
`ContratosExportados`, aplicadas desde ArquitecturaHexagonalTest. No se crea
otro check obligatorio ni se cambian los nombres configurados en GitHub.

Los puertos de entrada (`application.port.in`) y los DTO de aplicación
(`application.dto`) solo dependen de tipos Java, shared.domain y los puertos/DTO
exportados de su propia capacidad. No exponen modelos internos del dominio,
entidades, repositorios ni tipos Spring/JPA. Esto refuerza la frontera existente
sin necesitar que un consumidor ya invoque el contrato.

La comprobación incluye retornos, parámetros, campos, arrays, tipos heredados y
genéricos, también los anidados. Un DTO con un campo privado de dominio tampoco
es un envoltorio válido. El mensaje identifica la dependencia y sugiere usar
un DTO exportado. Los puertos de salida propios pueden usar modelos del dominio:
esta restricción no se aplica a application.port.out.

ContratosExportadosRegresionTest verifica casos válidos e inválidos: la forma del
PR #5 (retorno directo de dominio sin consumidor), genéricos, parámetros, arrays,
herencia, persistencia, Page de Spring y DTO que ocultan dominio. Las fixtures
intencionalmente inválidas están únicamente en src/test; la revisión de producción
las excluye. No son implementaciones de roles ni ejemplos para copiar a src/main.

CI verde acredita las verificaciones presentes, no ausencia de todos los defectos.
Los tipos dinámicos dentro de Object/contenedores sin tipar, SQL, propiedad de tablas,
reglas funcionales e integración real necesitan pruebas específicas y revisión.
Se mantiene la aprobación humana obligatoria; no fusionar solo por ver checks verdes.

### Evidencia inicial — 14-oct-2026

Preparado en codex/ci-contratos-internos desde develop 52fd0e6.
Java 21.0.10 / Gradle 8.14.3: comprobación de arquitectura 18/18 y clean build
completo 51/51, sin fallos ni omisiones, con bootJar y contextLoads correctos.
Contenedores locales desechables; no conexión a Supabase. No acredita aún una
ejecución en GitHub Actions: la rama debe publicarse mediante PR a develop.
