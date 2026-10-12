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
bash ./gradlew --no-daemon test --tests '*ArquitecturaHexagonalTest'
```

Las reglas permiten paquetes aún sin clases en el scaffold y se aplican cuando
se implementan. Comprueban dependencias de clases, no el SQL ni la propiedad de
tablas por sí solos; esa parte también requiere revisión y pruebas funcionales.
