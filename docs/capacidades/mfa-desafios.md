# MfaPort — desafíos de login (EP-04-T002)

Dueño: Luis David Morales Brenis. Actualización 15-oct-2026: composición productiva
con CuentasPort, AutorizacionPort y CredencialesPort incorporado en #28.
MfaConfig publica un único MfaPort decorado; MfaService no se expone como bean.
No incluye envío/verificación OTP, configuración MFA ni apertura de sesión.

## Contrato para autenticación y perfil

| Operación | Resultado |
| --- | --- |
| esEfectivo(boolean, Set<Rol>) | Política pura: voluntario o cualquier Rol.esDeGestion() |
| crearDesafio(UUID, RevisionCredencialMfa) | Creado, NoRequerido o Rechazado.INVALIDO |
| leerEstado(UUID, Set<Rol>) | Optional<EstadoMfa>, efectivo y obligatorio |

Rol procede de shared.domain y los roles actuales se obtienen del puerto de
Sergio, nunca del navegador. CLIENTE/VENDEDOR siguen la bandera voluntaria.
El DTO propio RevisionCredencialMfa conserva usuarioId/credencialId/version;
credencialId es la PK usuario_id real, igual que en el proveedor de #28.
No contiene hashes ni prueba OTP. El puente traduce ese DTO a RevisionCredencial
únicamente en adapter.out.capacidad; no importa entidades ni repositorios ajenos.

## Secuencia de consumo

1. El coordinador preverifica la contraseña con CredencialesPort fuera del lock.
   Solo una coincidencia real permite entrar en la rama de MFA.
2. Abre su transacción y bloquea la cuenta mediante CuentasPort.bloquear.
3. Copia la revisión previa a RevisionCredencialMfa y llama al MfaPort inyectado.
   crearDesafio es MANDATORY: revalida cuenta disponible, correo verificado,
   revisión actual y roles reales. Sin transacción falla antes de escribir.
4. Devuelve el resultado público únicamente después del commit. Creado contiene
   desafío aleatorio/hash persistido, EMAIL y 300 segundos; no emite acceso,
   refresco, OTP ni resetea racha. Un error técnico revierte el desafío.

CuentasMfaAdapter usa disponibilidad base y efectiva; RolesMfaAdapter consume
AutorizacionPort.resolver; RevisionCredencialMfaAdapter usa la confirmación
escalar actual de #28, sin depender de la caché JPA. No hay dobles en producción.

leerEstado es readOnly. Quien proyecta perfil entrega roles actuales resueltos;
la bandera voluntaria proviene de CuentasPort. esEfectivo no hace I/O.

ValidadorDesafioLogin es consulta interna, sin endpoint ni prueba de autenticación:
comprueba hash, vigencia, consumo, cuenta, MFA y revisión. Su caller debe tener
transacción y respetar locks al confirmar la revisión. Solicitar/verificar OTP
revalidarán bajo sus locks en T003/T004. El vencimiento exacto ya es inválido.

## Configuración y límites

Se reutiliza MfaCriptografiaConfig de T001: AUTH_OTP_HMAC_KEY_ID y
AUTH_OTP_HMAC_KEY (Base64, mínimo 32 bytes, externa y persistente). Sin clave
válida se deniega generación; no se crea material temporal en producción.
Los tests usan una clave sintética y los proveedores productivos.

V103 ya está propuesta en develop desde #9; este cambio no añade migración.
Christian conserva su revisión/aplicación coordinada y grants de runtime.
No se ejecutó DDL remoto ni se acredita despliegue o correo. Login HTTP completo,
OTP y proyección de perfil se integran en sus bloques posteriores consumiendo
este puerto. La revisión humana y el CI del nuevo head siguen siendo obligatorios.
