# FEAT-03-T1 — Tokens de verificación, recuperación y cambio de correo

**Estado:** implementación local y aceptación PostgreSQL ejecutada; queda coordinar el contrato con Eva y Valery.

## Base y fuentes

- Rama: `feat/EP-03-tokens-un-uso`, creada desde `develop` `4bd7b61165ff2e4940c68a8b0f050ba395b7b7ad`.
- Documentación H4: `Modulo-de-Seguridad` `main` `1b034d2b1db0a19bb5c12c1c5426c797f47864b5`; plan H4 v1.2 y `tareas/hito-4/juan-cano/TAREAS.md`, FEAT-03-T1.
- Fuentes funcionales: SPEC-02 (24 horas y uso único), SPEC-08 (máximo 30 minutos e invalidar anteriores), SPEC-16 (el correo nuevo se confirma mediante el mecanismo de SPEC-02), `BACKEND-HEXAGONAL.md`, `ACUERDOS-INTERNOS.md` y `DISENO-PERSISTENCIA.md`.

## Cambio

- Reutiliza `token_un_uso`; no añade repositorios ni migración. La columna existente `destino` queda reservada a `CAMBIO_CORREO`.
- `VERIFICAR_CORREO` vence a las 24 horas; `RECUPERACION` admite hasta 30 minutos; el nuevo método `emitirCambioCorreo(UUID usuarioId, String destino)` emite `CAMBIO_CORREO` por 24 horas.
- Persistencia y snapshot interno llevan el nuevo destino. El dominio exige destino solo para `CAMBIO_CORREO`; el `toString` del snapshot lo redacta.
- El hash SHA-256, la invalidación por usuario y propósito, la verificación del propósito y el consumo único reutilizan los mecanismos existentes.

## Verificación ejecutada

Con Microsoft JDK 21.0.12.1:

```powershell
.\gradlew.bat --no-daemon --console=plain test --tests '*TokensUnUsoServiceTest' --tests '*TokensUnUsoConfigTest' --tests '*ArquitecturaHexagonalTest' --tests '*ContratosExportados'
```

Resultado: `BUILD SUCCESSFUL`, 12 pruebas, 0 fallos, 0 errores y 0 omitidas (5 de servicio, 1 de wiring y 6 de arquitectura).

La clase `TokenUnUsoJpaPostgresTest` ejecutó sus 10 pruebas con PostgreSQL 16.6 en Testcontainers: 0 fallos, 0 errores y 0 omitidas. Incluye mapping de `destino`, TTL y aislamiento entre los tres propósitos, además de regresiones PostgreSQL existentes. En la primera ejecución el comando `docker` aún no estaba disponible en PowerShell, pero Testcontainers pudo iniciar y usar el contenedor.

Reejecución posterior forzada el 21-oct-2026, con Docker Desktop 4.96.0 / Engine 29.9.1 confirmado por `docker version`, mediante `gradlew.bat --no-daemon --console=plain test --rerun-tasks --tests '*TokenUnUsoJpaPostgresTest'`: `BUILD SUCCESSFUL`, 10/10 aprobadas, 0 fallos, 0 errores y 0 omitidas.

Verificación consolidada de T1: 22 pruebas seleccionadas (servicio, wiring, arquitectura y PostgreSQL), 0 fallos, 0 errores y 0 omitidas; `BUILD SUCCESSFUL` con Microsoft JDK 21.0.12.1. Esta ejecución valida la fixture local derivada de `schema.sql` y las migraciones propuestas; no se conectó a Supabase ni se aplicaron migraciones en esa base.

## Contrato interno para coordinar

```java
TokenEmitido emitirCambioCorreo(UUID usuarioId, String destino);
Optional<TokenUnUsoSnapshot> localizar(String hash, PropositoToken proposito);
```

Para `CAMBIO_CORREO`, `TokenUnUsoSnapshot.destino()` devuelve el correo pendiente de confirmación y su representación textual lo redacta. Esta firma está preparada en código; aún debe compartirse y confirmarse con Eva y Valery.
