# MfaPort — núcleo del desafío de login (EP-04-T002)

Dueño: Luis David Morales Brenis. Entrega parcial: núcleo y pruebas locales.
No existe todavía wiring productivo ni puentes a proveedores reales.
MfaPort no es un endpoint ni autoriza activar MFA, verificar contactos o abrir sesión.

## Contrato interno para Jose Luis y Valery

| Operación | Resultado |
|---|---|
| esEfectivo(boolean voluntario, Set<String> rolesActuales) | Boolean: basta cualquier rol de gestión para exigir MFA |
| crearDesafio(UUID usuarioId, RevisionCredencialMfa revision) | ResultadoCreacionDesafio: Creado, NoRequerido o Rechazado.INVALIDO |
| leerEstado(UUID usuarioId, Set<String> rolesActuales) | Optional<EstadoMfa>: mfaHabilitado efectivo y obligatorio |

Roles son códigos canónicos del proveedor de Sergio; no se leen del navegador
ni de perfiles de negocio locales. CLIENTE/VENDEDOR siguen bandera voluntaria,
y ADMIN_VENTAS/GESTOR_DESPACHO/GESTOR_COMERCIAL/ADMIN_SISTEMA siempre obligan.
Un rol desconocido causa error técnico/de composición en vez de permitir omitir MFA.
La política no administra el catálogo, asociaciones ni permisos de Sergio.

RevisionCredencialMfa contiene usuarioId/credencialId/version y procede de la
preverificación de Juan José; no acepta contraseña/hash ni prueba de OTP.
El consumidor la convierte desde el DTO real del dueño y confirma su vínculo
mediante el puente; no construirla desde un cuerpo HTTP para eludir autenticación.
Cualquier inexistencia/indisponibilidad o revisión sustituida devuelve el mismo
rechazo interno. Jose Luis mantiene su 401 genérico y sus reglas de login.

Creado contiene DesafioLogin con challengeToken/canal/expiraEn y
mfaRequerido() = true. El mapper del login produce exactamente la rama DesafioMfa
de OpenAPI: mfaRequerido true, challengeToken, canal EMAIL, expiraEn 300.
Nunca serializar automáticamente los resultados internos como respuesta HTTP.
NoRequerido deja al coordinador completar su recorrido sin MFA; no es una sesión.
Leer estado permite proyección de atributos; autorización/titularidad del endpoint
siguen siendo del coordinador de Valery/Eva.

## SOLID, puertos y composición

MfaService y ValidadorDesafioLogin son Java puro, stateless, con constructor
y dependencias final. La política queda en dominio, los DTO exportados no
referencian dominio/JPA/Spring. CuentasMfaPort, RolesMfaPort y
RevisionCredencialMfaPort son salidas estrechas de este consumidor, no
implementaciones alternativas de CuentasPort/AutorizacionPort/CredencialesPort.

T002 usa AlmacenDesafiosMfaPort y CriptografiaDesafiosMfaPort, sin obligar a sus
casos de uso a depender de operaciones OTP. Los puertos completos de T001
extienden esas interfaces y conservan sus métodos originales por herencia.
Los adaptadores existentes siguen implementando ambos contratos sin delegación
a adaptadores de otro dueño ni duplicación de lógica criptográfica.

Puentes futuros en mfa/adapter/out/capacidad deben usar exclusivamente puertos
de entrada y DTO exportados del dueño. No implementar aquí repositorios, SQL
o clases entity de cuentas, credenciales o roles. Ningún doble se registra en src/main.

## Transacciones y validación de desafío

MfaTransaccional es el decorador que debe registrarse explícitamente como único
bean MfaPort cuando estén los puentes reales. No se auto-registra un servicio
incompleto ni se habilitan mocks en producción. Wiring: crear el núcleo con
sus salidas reales, Clock y UUID::randomUUID y devolver el decorador desde
@Configuration; el coordinador debe recibir ese bean proxificado por Spring.

Crear usa MANDATORY: Jose Luis abre su transacción, bloquea la cuenta mediante
Eva y entrega revisión preverificada. Bajo esa frontera Mfa revalida cuenta,
correo verificado, revisión y roles actuales. Guarda desafío sin familia, tokens
de acceso/refresco, OTP, envío o reset de racha. El coordinador entrega HTTP
solo después del commit; los fallos técnicos revierten. No REQUIRES_NEW.

ValidadorDesafioLogin.localizarVigente es consulta interna de MFA, no puerto
exportado ni autenticación completa. Calcula hash mediante criptografía de T001,
comprueba existencia, usuario, fechas, consumo, MFA efectivo y revisión actual.
El vencimiento exacto ya es inválido; históricos sin revisión no autorizan.
No consume desafío ni OTP, y no aplica la caducidad de contraseña antes de MFA.
T003/T004 deben revalidar en su transacción con cuenta→desafío→OTP bloqueados,
aplicar las reglas de cuota/código y caducidad y componer sesión/auditoría.

## Dependencias pendientes

Esta rama parte del commit 0395a de EP-04-T001, no copia sus cambios locales
sin commit. Integra su historial existente; el PR hacia develop debe permanecer
en borrador hasta que se incorpore/revise su dependencia (PR #9).
Las mejoras de tests de T001 y el CI ampliado se incorporan mediante sus PR.

Faltan: contraste/aplicación de V103 con Christian; DTO/puentes reales de Eva,
Juan José y Sergio; wiring único de MfaPort; prueba con el login real de Jose Luis
y la proyección de Valery. No se afirma integración real, despliegue ni correo.
T003/T004 y el cambio MFA de SPEC-10 no forman parte de esta entrega.
