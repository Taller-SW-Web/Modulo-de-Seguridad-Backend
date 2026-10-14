# Persistencia de protección — EP-07-T001

Dueño: Luis David Morales Brenis. Esta entrega es interna a proteccion; no
implementa todavía ProteccionPort ni conteo funcional, correo, enlaces o HTTP.

## Fronteras y transacciones

- PersistenciaProteccionPort y HuellaProteccionPort son puertos de salida propios.
  Sus modelos de dominio no se exportan a otra épica. T002 publicará su puerto
  de entrada y DTO bajo application.dto, respetando BACKEND-HEXAGONAL.
- ProteccionJpaAdapter escribe solo intento_login, bloqueo, racha_fallo y
  huella_fallo. No lee/escribe usuario, credencial, sesión, auditoría o outbox.
- Mutaciones y locks exigen MANDATORY. El coordinador futuro toma antes los
  locks del catálogo de administrador y cuenta mediante sus dueños. Una consulta
  sin fila no asegura ningún lock; crear racha exige lock previo de la cuenta.
- crearRacha inserta, no sobreescribe un key id. reiniciarRacha exige otro UUID,
  bloquea la racha actual, borra sus huellas y guarda la nueva en el mismo commit.
  No cambia contadores de usuario; esa escritura pertenece a Eva.
- insertarHuellaSiNueva usa PK y ON CONFLICT DO NOTHING: la repetición devuelve
  false y conserva la transacción utilizable. T002 aplica el máximo de cinco
  bajo lock; no contar filas de intento_login como contador funcional.
- registrarIntento realiza INSERT explícito. Repetir un id no modifica el hecho.
  No hay purga temporal de rachas/huellas. Intentos mantienen retención de SPEC-14.
- eliminarBloqueo exige la revisión esperada. El caso de uso futuro comprueba
  AUTOMATICO/propósito/usuario/token; este adaptador interno no autoriza desbloqueos.
  Lectura del bloqueo vuelve a consultar DB, evitando devolver una fila eliminada
  por bulk delete desde la caché de primer nivel.

## Huellas y secretos externos

AUTH_FALLOS_HMAC_KEY_ID identifica el pepper actual (hasta 50 caracteres sin
espacios iniciales/finales); AUTH_FALLOS_HMAC_KEY es Base64 estándar de al menos
32 bytes aleatorios. Es un secreto distinto de contraseña JDBC, clave RSA y
clave OTP. Christian configura y conserva el mismo material por entorno.
No se genera clave temporal ni se proporciona una clave real en Git.

La huella usa HMAC-SHA256 y almacena 64 caracteres hex minúsculos. El mensaje
contiene cinco campos prefijados por longitud uint32 big-endian: G7-FALLO-v1,
FALLO_LOGIN, UUID de usuario en texto canónico, UUID de racha y bytes exactos
de la contraseña incorrecta. No convertir/normalizar el secreto; el llamador
debe borrar su buffer efímero al terminar. El adaptador copia y limpia su copia.

RachaFallo guarda hash_key_id; la generación utiliza esa clave, incluso si
otra es la actual. El constructor admite un registro de claves anteriores,
copiando su material. Wiring H3 carga una sola clave actual: no habilitar una
rotación operativa que abandone rachas existentes sin sus claves anteriores.
Si falta configuración o clave de la racha, se deniega generación; T002 no
debe sustituir esa situación por un nuevo contador o éxito de autenticación.
Modelos, entidades y adaptador tienen representaciones redactadas.

## Esquema y aceptación pendiente

V104 permanece en docs/migraciones-propuestas, fuera de Flyway automático.
Añade UUID revision a bloqueo (históricos reciben UUID sin alterar tipo/hasta)
y las tablas de racha/huella con PK, FK compuesta e integridad del hash.
El mapping char(64) declara JDBC CHAR explícitamente para Hibernate/PostgreSQL.
Las nuevas tablas tienen RLS activada; grants y políticas del runtime quedan
bajo TEC-INFRA-T002. Las pruebas con owner no demuestran esos grants.

Las pruebas usan V1–V4 exactas conservadas en recursos de test y PostgreSQL 16.6
desechable. No se usa el antiguo backend como aplicación ni se ejecuta V100.
No acreditan el DDL manual remoto, PostgreSQL 17.6, despliegue o APIs. Consultar
REVISION-V104 y la evidencia antes de aplicar/promover la migración.
