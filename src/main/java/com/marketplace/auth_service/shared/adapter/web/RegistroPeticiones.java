package com.marketplace.auth_service.shared.adapter.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Datos que el registro de peticiones va juntando mientras se atiende una petición
 * (docs/operacion/LOGS.md). Viven como atributos de la petición, nunca en el cuerpo ni en
 * una sesión.
 *
 * <ul>
 *   <li>El número de seguimiento ({@value #CABECERA_ID}) que lleva cada línea de log y cada error.</li>
 *   <li>El error del contrato que se respondió, anotado por {@link Problemas} al construirlo.</li>
 *   <li>Quién llamó, anotado por {@link CapturaLlamante} cuando la autenticación tuvo éxito.</li>
 * </ul>
 */
public final class RegistroPeticiones {

    /** Cabecera de entrada y de salida con el número de seguimiento. */
    public static final String CABECERA_ID = "X-Request-Id";
    /** Clave del MDC: aparece en todas las líneas de log de la petición. */
    public static final String MDC_ID = "idPeticion";

    static final String ATRIBUTO_ID = RegistroPeticiones.class.getName() + ".id";
    static final String ATRIBUTO_PROBLEMA = RegistroPeticiones.class.getName() + ".problema";
    static final String ATRIBUTO_LLAMANTE = RegistroPeticiones.class.getName() + ".llamante";

    /** Lo que se acepta de otro módulo: corto y sin espacios, saltos de línea ni separadores del log. */
    private static final Pattern ID_ACEPTABLE = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private RegistroPeticiones() {
    }

    /**
     * Reutiliza el número que mandó quien llama si es seguro escribirlo en el log; si no, crea
     * uno nuevo. Nunca se copia un valor arbitrario al log (inyección de líneas).
     */
    static String idDe(String recibido) {
        if (recibido != null && ID_ACEPTABLE.matcher(recibido).matches()) {
            return recibido;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Lo llama {@link Problemas} con cada error del contrato: guarda el error para la línea de
     * log y le pone el número de seguimiento en {@code instance} (campo ya previsto en el
     * esquema {@code Problem}: «referencia de la petición, útil para soporte»).
     */
    static void anotar(ProblemDetail problema) {
        RequestAttributes peticion = RequestContextHolder.getRequestAttributes();
        if (peticion == null) {
            return;
        }
        peticion.setAttribute(ATRIBUTO_PROBLEMA, problema, RequestAttributes.SCOPE_REQUEST);
        Object id = peticion.getAttribute(ATRIBUTO_ID, RequestAttributes.SCOPE_REQUEST);
        if (id instanceof String valor) {
            problema.setInstance(URI.create("urn:g7:peticion:" + valor));
        }
    }
}
