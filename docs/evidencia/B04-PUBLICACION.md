# Publicación manual B04 — EP-07-T002/T003/T004

Preparado sobre develop 87fda630385a2e96ab4bd16b80f19f53c67cc33f.
Un commit de integración por el núcleo/wiring/pruebas compartidos; evidencia
y estado separados por ID. No se cierra ningún ID por el CI.

## Git Bash

```bash
cd /d/desarrollo-uni/ciclo8/Taller/Modulo-de-Seguridad/Backend-auth-service/.worktrees/h3-b04-proteccion
git add src/main/java/com/marketplace/auth_service/proteccion \
  src/main/java/com/marketplace/auth_service/configuration/ProteccionConfig.java \
  src/main/java/com/marketplace/auth_service/configuration/SeguridadConfig.java \
  src/test/java/com/marketplace/auth_service/proteccion \
  docs/evidencia/EP-07-T00{2,3,4}.md docs/evidencia/B04-PUBLICACION.md
git diff --cached --stat
git commit -m "feat: protección y desbloqueo B04 (EP-07-T002/T003/T004)"
git push -u origin codex/h3-b04-proteccion
```

## PR a develop

Título: B04: protección y desbloqueo — EP-07-T002/T003/T004 (parcial)

Añade ProteccionPort, conteo HMAC persistente, clasificación automático/manual
y casos de uso de bloqueo progresivo y desbloqueo por enlace. Reutiliza T001
y consume cuentas/guard/escritura reales, con transacciones y locks acordados.

79 pruebas seleccionadas, cero fallos/errores/omisiones; 29 PostgreSQL.
La progresión y enlaces se prueban con dobles transaccionales SOLO de test,
sin presentarlos como integración de #20 ni de Christian.

Pendientes: puente a TokensUnUsoPort de #20, auditoría/evento/correo durable
reales y aceptación conjunta. Sin proveedores, las transiciones revierten;
el desbloqueo productivo devuelve 503, nunca un 204 ficticio.
No cierra los IDs, no modifica #28/#29/#16 y no ejecuta SQL remoto.
