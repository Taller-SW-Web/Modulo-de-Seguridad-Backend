package com.marketplace.auth_service.shared.adapter.web;

import org.springframework.http.ProblemDetail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Una línea del registro de peticiones, legible de izquierda a derecha:
 *
 * <pre>
 * modulo-despacho | GET /api/v1/usuarios/{id} | 403 SCOPE_INSUFICIENTE | ... | 11 ms | id=9b21f0c4a1d2e3f4 | [CLIENTE]
 * </pre>
 *
 * Quién llamó, qué pidió, qué respondimos, por qué, cuánto tardó, el número de seguimiento y de
 * quién es el problema. Cómo leerla: {@code docs/operacion/LOGS.md}.
 *
 * <p>Nunca lleva tokens, contraseñas, códigos, secretos, correos ni el cuerpo de la petición: solo
 * nombres de campos y de reglas, y el {@code client_id} del módulo.
 */
public record LineaPeticion(String llamante, String metodo, String ruta, int estado, String codigo, String motivo,
                            long milisegundos, String id, Responsable responsable) {

    /** A partir de cuánto se marca la petición como lenta (el arranque en frío de Render ronda los 50 s). */
    public static final long UMBRAL_LENTO_MS = 2_000;

    /** De quién es el problema. */
    public enum Responsable {
        /** Respondió bien. */
        OK,
        /** Lo envió mal quien llamó (4xx): token, scope, cuerpo, límite, recurso inexistente. */
        CLIENTE,
        /** Falla nuestra o de algo de lo que dependemos (5xx): base de datos, correo, auditoría, error inesperado. */
        NOSOTROS
    }

    private static final Pattern TEXTO_SEGURO = Pattern.compile("[A-Za-z0-9._:-]{1,40}");

    /** Motivo legible por código del catálogo de errores (specs/catalogo-errores.md del repo de documentación). */
    private static final Map<String, String> MOTIVOS = Map.ofEntries(
            Map.entry("VALIDACION", "el cuerpo no cumple el contrato"),
            Map.entry("LOTE_DEMASIADO_GRANDE", "la consulta por lote trae más de 100 identificadores"),
            Map.entry("TOKEN_INVALIDO", "token ausente, mal formado, vencido o con firma que no valida"),
            Map.entry("CREDENCIALES_INVALIDAS",
                    "correo o contraseña incorrectos, o la cuenta no está ACTIVO (no se distingue a propósito)"),
            Map.entry("REFRESCO_INVALIDO", "el token de refresco no existe, ya se usó o fue revocado"),
            Map.entry("CODIGO_INVALIDO", "el código OTP no coincide con el desafío"),
            Map.entry("CLIENTE_INVALIDO", "client_id desconocido o inactivo, o client_secret incorrecto"),
            Map.entry("CUENTA_NO_DISPONIBLE", "la cuenta no se puede bloquear en su estado actual"),
            Map.entry("TOKEN_NO_APLICABLE", "un token de módulo intentó una operación que solo hace una persona"),
            Map.entry("SCOPE_INSUFICIENTE", "el token es válido pero le falta el scope o permiso que exige esta ruta"),
            Map.entry("NO_ENCONTRADO", "el recurso pedido no existe"),
            Map.entry("CORREO_NO_DISPONIBLE", "el correo ya está registrado"),
            Map.entry("CLIENTE_SERVICIO_EXISTENTE", "ya existe un cliente técnico con ese clientId"),
            Map.entry("DOCUMENTO_NO_DISPONIBLE", "el documento ya pertenece a otra cuenta"),
            Map.entry("DEMASIADAS_SOLICITUDES", "superó el límite de intentos; debe esperar (Retry-After)"),
            Map.entry("NO_DISPONIBLE", "una dependencia nuestra no respondió: base de datos, correo, auditoría o clave de firma"),
            Map.entry("ENLACE_EXPIRADO", "el enlace del correo ya venció"),
            Map.entry("ENLACE_YA_USADO", "el enlace del correo ya se usó o no existe"),
            Map.entry("POLITICA_INCUMPLIDA", "la contraseña no cumple la política"),
            Map.entry("PASSWORD_CADUCADA", "la contraseña de gestión tiene 90 días o más; debe restablecerla"),
            Map.entry("TOKEN_RECUPERACION_INVALIDO", "el enlace de recuperación no existe, ya se usó o fue reemplazado"),
            Map.entry("TOKEN_RECUPERACION_EXPIRADO", "el enlace de recuperación tiene más de 30 minutos"),
            Map.entry("OTP_EXPIRADO", "el código OTP tiene más de 5 minutos"),
            Map.entry("OTP_INTENTOS_AGOTADOS", "se agotaron los 3 intentos del código OTP"),
            Map.entry("MFA_OBLIGATORIO", "una cuenta de gestión no puede desactivar su segundo factor"),
            Map.entry("EXPORTACION_DEMASIADO_GRANDE", "la exportación abarca más de 100 000 registros"),
            Map.entry("TOKEN_DESBLOQUEO_INVALIDO", "el enlace de desbloqueo no existe, ya se usó o fue reemplazado"),
            Map.entry("TOKEN_DESBLOQUEO_EXPIRADO", "el enlace de desbloqueo tiene más de 30 minutos"),
            Map.entry("ADMINISTRADOR_PROTEGIDO", "acción sobre la propia cuenta o sobre el último ADMIN_SISTEMA activo"),
            Map.entry("ATRIBUTO_NO_APLICABLE", "atributo de vendedor en un cliente, o al revés"));

    /** Motivo cuando el error no trae código del contrato (los arma Spring: ruta o método inexistente...). */
    private static final Map<Integer, String> MOTIVOS_SIN_CODIGO = Map.of(
            400, "petición mal formada (cuerpo ilegible o parámetro inválido)",
            401, "falta autenticarse",
            403, "acceso denegado",
            404, "la ruta no existe",
            405, "método HTTP no permitido en esta ruta",
            406, "formato de respuesta no soportado (cabecera Accept)",
            415, "Content-Type no aceptado en esta ruta",
            500, "error inesperado en nuestro servicio",
            503, "servicio no disponible");

    /** Responsable según el código HTTP: 4xx es de quien llama y 5xx es nuestro. */
    public static Responsable responsableDe(int estado) {
        if (estado >= 500) {
            return Responsable.NOSOTROS;
        }
        return estado >= 400 ? Responsable.CLIENTE : Responsable.OK;
    }

    /** Motivo legible a partir del error respondido; solo nombres de campos y reglas, nunca valores. */
    public static String motivoDe(int estado, ProblemDetail problema) {
        String codigo = codigoDe(problema);
        if (codigo == null) {
            if (estado < 300) {
                return estado == 204 ? "correcto (sin contenido)" : "correcto";
            }
            if (estado < 400) {
                return "redirección";
            }
            return MOTIVOS_SIN_CODIGO.getOrDefault(estado, estado >= 500 ? "error en nuestro servicio" : "petición rechazada");
        }
        String base = MOTIVOS.getOrDefault(codigo, problema.getTitle() == null ? "error " + codigo : problema.getTitle());
        List<String> detalles = detallesDe(problema);
        if (detalles.isEmpty()) {
            return base;
        }
        String tipo = "POLITICA_INCUMPLIDA".equals(codigo) ? "reglas" : "campos";
        return base + ": " + tipo + " " + String.join(", ", detalles);
    }

    public static String codigoDe(ProblemDetail problema) {
        if (problema == null || problema.getProperties() == null) {
            return null;
        }
        Object codigo = problema.getProperties().get("code");
        return codigo instanceof String texto && !texto.isBlank() ? texto : null;
    }

    /**
     * Nombres de campo ({@code errores[].campo}) o de regla ({@code errores[].regla}) del error.
     * Los mensajes no se copian: algunos pueden repetir lo que escribió la persona.
     */
    private static List<String> detallesDe(ProblemDetail problema) {
        Object errores = problema.getProperties() == null ? null : problema.getProperties().get("errores");
        if (!(errores instanceof Collection<?> lista)) {
            return List.of();
        }
        List<String> nombres = new ArrayList<>();
        for (Object error : lista) {
            if (error instanceof Map<?, ?> mapa) {
                Object nombre = mapa.containsKey("campo") ? mapa.get("campo") : mapa.get("regla");
                if (nombre instanceof String texto && TEXTO_SEGURO.matcher(texto).matches() && !nombres.contains(texto)) {
                    nombres.add(texto);
                }
            }
            if (nombres.size() == 5) {
                break;
            }
        }
        return nombres;
    }

    /** Lo que se escribe cuando la ruta no coincide con ninguna declarada en el backend. */
    public static final String RUTA_NO_RECONOCIDA = "(ruta no reconocida)";

    /**
     * Ruta con plantilla: {@code /api/v1/usuarios/{id}}, nunca el identificador real. Solo se
     * escriben plantillas declaradas en el backend; si no hay ninguna, una etiqueta fija. Nunca se
     * copia un trozo de la URL recibida: podría llevar un token o un secreto.
     */
    public static String rutaDe(String plantilla) {
        if (plantilla == null || plantilla.isBlank() || "/**".equals(plantilla)) {
            return RUTA_NO_RECONOCIDA;
        }
        return plantilla;
    }

    /** Un texto recibido de fuera, apto para el log; si no, se dice que no era válido. */
    public static String limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return TEXTO_SEGURO.matcher(texto).matches() ? texto : "(valor no válido)";
    }

    public boolean lenta() {
        return milisegundos >= UMBRAL_LENTO_MS;
    }

    /** La línea legible. */
    public String texto() {
        return llamante
                + " | " + metodo + " " + ruta
                + " | " + estado + (codigo == null ? "" : " " + codigo)
                + " | " + motivo
                + " | " + milisegundos + " ms" + (lenta() ? " LENTO" : "")
                + " | id=" + id
                + " | [" + responsable + "]";
    }

    /** Los mismos datos por separado, para el log en JSON y para filtrar. */
    public Map<String, String> campos() {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("llamante", llamante);
        campos.put("metodo", metodo);
        campos.put("ruta", ruta);
        campos.put("estado", Integer.toString(estado));
        campos.put("codigo", codigo == null ? "" : codigo);
        campos.put("motivo", motivo);
        campos.put("ms", Long.toString(milisegundos));
        campos.put("lenta", Boolean.toString(lenta()));
        campos.put("responsable", responsable.name());
        return campos;
    }
}
