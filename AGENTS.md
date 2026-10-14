# Guía para IA — backend G7

Este es el repositorio de código `Modulo-de-Seguridad-Backend`. La fuente SDD es
el clon de `Taller-SW-Web/Modulo-de-Seguridad`; no inventar contratos si falta acceso.
En la disposición local actual ese clon es el padre, pero no se requiere anidar
los repositorios. Abrir ambos clones y resolver las fuentes desde su propia raíz.

Antes de editar: leer AGENTS.md y CONTEXT.md de documentación, el diseño
`plan/hito-3/BACKEND-HEXAGONAL.md`, los tres documentos comunes H3, el plan y
tablero del dueño. Registrar la revisión documental consumida y el ID de tarea.
Implementar una tarea autorizada por vez; no marcar pruebas/evidencia inexistentes.

- Java 21, Boot 3.5.16, Gradle wrapper 8.14.3; paquete com.marketplace.auth_service.
  No Maven, Boot 4 ni reutilización de la base histórica descartada.
- Núcleo domain/application independiente de Spring, JPA, Jackson y HTTP.
  Puertos definidos dentro; adaptadores implementan los puertos; wiring fuera.
- DTO HTTP separados de DTO de aplicación y modelos; mappers manuales.
  Entidades JpaEntity y repositorios JpaRepository solo dentro de persistencia.
- Los puertos de entrada y DTO exportados solo usan tipos Java/shared.domain y
  puertos/DTO propios, incluidos campos y genéricos. No envolver un modelo interno
  en un DTO ni devolver Page de Spring. CI lo verifica aun sin consumidores;
  los puertos de salida internos sí pueden usar su dominio. Ver .github/CI.md.
- Cada tabla tiene un único dueño físico. Consumir al otro por puertos/DTO
  exportados mediante un puente; sin repositorios, SQL o entidades ajenos.
- Transacciones en decoradores, conservando orden de locks y commit de defensas;
  efectos externos después del commit. No deshacer un bloqueo por lanzar el 401.
- JDBC/Supabase con secretos externos; no migrar una base desconocida ni usarla
  en tests automáticos. No reset/baseline/repair ni semillas ciegas.
- OpenAPI, errores y eventos se consultan en documentación y no se cambian aquí.
  No enumerar cuentas ni registrar credenciales, tokens, OTP o datos cifrados claros.

Verificar compilación Gradle, tests de núcleo/mappers, arquitectura y pruebas
PostgreSQL/HTTP aplicables. Los tests con dobles no acreditan integración real.
No hacer commits/push ni implementar otra tarea salvo autorización del usuario.
