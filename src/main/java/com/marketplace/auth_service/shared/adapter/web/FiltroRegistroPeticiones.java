package com.marketplace.auth_service.shared.adapter.web;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Registro de peticiones (docs/operacion/LOGS.md). Por cada petición:
 *
 * <ol>
 *   <li>Toma el número de seguimiento de {@code X-Request-Id} si es seguro, o crea uno, y lo
 *       devuelve en la misma cabecera. Los errores lo repiten en {@code instance}.</li>
 *   <li>Lo pone en el MDC: cualquier otra línea de log de esa petición lo lleva.</li>
 *   <li>Al terminar, escribe una línea en el logger {@value #LOGGER}: quién llamó, qué pidió,
 *       qué respondimos, por qué, cuánto tardó y de quién es el problema.</li>
 * </ol>
 *
 * <p>Nivel: {@code INFO} si salió bien, {@code WARN} si lo envió mal quien llamó, {@code ERROR} si
 * es nuestro. La comprobación de salud con 200 no se registra: Render la llama cada pocos segundos.
 */
public final class FiltroRegistroPeticiones extends OncePerRequestFilter {

    public static final String LOGGER = "g7.peticiones";
    private static final Logger log = LoggerFactory.getLogger(LOGGER);
    private static final String SALUD = "/actuator/health";
    private static final String TOKEN_SERVICIO = "/api/v1/auth/token";

    /** Busca la plantilla de la ruta cuando Spring no eligió controlador; {@code null} si no hay. */
    private final Function<HttpServletRequest, String> plantillas;

    public FiltroRegistroPeticiones() {
        this(peticion -> null);
    }

    public FiltroRegistroPeticiones(Function<HttpServletRequest, String> plantillas) {
        this.plantillas = Objects.requireNonNull(plantillas, "plantillas");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (isAsyncDispatch(peticion)) {
            // La respuesta asíncrona sigue en otro hilo: solo hace falta el número en el MDC.
            Object id = peticion.getAttribute(RegistroPeticiones.ATRIBUTO_ID);
            try (var ignorado = MDC.putCloseable(RegistroPeticiones.MDC_ID, id == null ? null : id.toString())) {
                cadena.doFilter(peticion, respuesta);
            }
            return;
        }
        String id = RegistroPeticiones.idDe(peticion.getHeader(RegistroPeticiones.CABECERA_ID));
        peticion.setAttribute(RegistroPeticiones.ATRIBUTO_ID, id);
        respuesta.setHeader(RegistroPeticiones.CABECERA_ID, id);
        long inicio = System.nanoTime();
        Throwable fallo = null;
        // Problemas anota el error por RequestContextHolder; Spring lo enlaza más adelante, pero
        // así también queda anotado lo que ocurra antes (o en contextos sin ese filtro).
        boolean enlazado = RequestContextHolder.getRequestAttributes() == null;
        if (enlazado) {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion, respuesta));
        }
        try (var ignorado = MDC.putCloseable(RegistroPeticiones.MDC_ID, id)) {
            cadena.doFilter(peticion, respuesta);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            fallo = e;
            throw e;
        } finally {
            if (enlazado) {
                RequestContextHolder.resetRequestAttributes();
            }
            if (fallo == null && peticion.isAsyncStarted()) {
                peticion.getAsyncContext().addListener(new AlTerminar(peticion, respuesta, inicio, id, plantillas));
            } else {
                registrar(peticion, respuesta, inicio, id, fallo, plantillas);
            }
        }
    }

    private static void registrar(HttpServletRequest peticion, HttpServletResponse respuesta, long inicio, String id,
                                  Throwable fallo, Function<HttpServletRequest, String> plantillas) {
        try {
            int estado = fallo == null ? respuesta.getStatus() : 500;
            if (estado == 200 && SALUD.equals(peticion.getRequestURI())) {
                return;
            }
            LineaPeticion linea = linea(peticion, estado, inicio, id, fallo, plantillas);
            escribir(linea);
        } catch (RuntimeException e) {
            // El registro nunca debe cambiar la respuesta.
            log.debug("No se pudo escribir la línea de la petición {}", id, e);
        }
    }

    static LineaPeticion linea(HttpServletRequest peticion, int estado, long inicio, String id, Throwable fallo,
                              Function<HttpServletRequest, String> plantillas) {
        ProblemDetail problema = peticion.getAttribute(RegistroPeticiones.ATRIBUTO_PROBLEMA) instanceof ProblemDetail p
                ? p : null;
        String codigo = LineaPeticion.codigoDe(problema);
        String motivo = fallo == null
                ? LineaPeticion.motivoDe(estado, problema)
                : "error inesperado en nuestro servicio (" + causaDe(fallo).getClass().getSimpleName() + ")";
        Object elegida = peticion.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String plantilla = elegida == null ? null : elegida.toString();
        if (plantilla == null || "/**".equals(plantilla)) {
            plantilla = plantillas.apply(peticion);
        }
        String ruta = LineaPeticion.rutaDe(plantilla);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        return new LineaPeticion(llamanteDe(peticion, estado), peticion.getMethod(), ruta, estado, codigo, motivo, ms, id,
                LineaPeticion.responsableDe(estado));
    }

    /** La excepción real: Spring la envuelve en {@link ServletException} al salir del controlador. */
    static Throwable causaDe(Throwable fallo) {
        Throwable causa = fallo;
        while (causa instanceof ServletException && causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        return causa;
    }

    /**
     * Quién llamó: el módulo o la persona del token validado; en {@code POST /auth/token}, el
     * {@code client_id} solo si el token se emitió (2xx): si falló, el valor recibido no se copia,
     * porque podría ser un secreto pegado por error. Si mandó un token que no se aceptó, se dice
     * así; si no mandó nada, {@code anónimo}.
     */
    static String llamanteDe(HttpServletRequest peticion, int estado) {
        Object capturado = peticion.getAttribute(RegistroPeticiones.ATRIBUTO_LLAMANTE);
        if (capturado instanceof String texto) {
            return texto;
        }
        if ("POST".equals(peticion.getMethod()) && TOKEN_SERVICIO.equals(peticion.getRequestURI())) {
            if (estado < 200 || estado >= 300) {
                return "cliente no autenticado (pide token)";
            }
            String clientId = LineaPeticion.limpiar(peticion.getParameter("client_id"));
            return (clientId == null ? "client_id ausente" : clientId) + " (pide token)";
        }
        String autorizacion = peticion.getHeader("Authorization");
        if (autorizacion != null && !autorizacion.isBlank()) {
            return "token no aceptado";
        }
        return "anónimo";
    }

    private static void escribir(LineaPeticion linea) {
        List<MDC.MDCCloseable> campos = new ArrayList<>();
        try {
            for (Map.Entry<String, String> campo : linea.campos().entrySet()) {
                campos.add(MDC.putCloseable(campo.getKey(), campo.getValue()));
            }
            campos.add(MDC.putCloseable(RegistroPeticiones.MDC_ID, linea.id()));
            switch (linea.responsable()) {
                case OK -> log.info(linea.texto());
                case CLIENTE -> log.warn(linea.texto());
                case NOSOTROS -> log.error(linea.texto());
            }
        } finally {
            campos.forEach(MDC.MDCCloseable::close);
        }
    }

    /** Cierra la línea de una respuesta asíncrona cuando de verdad terminó. */
    private record AlTerminar(HttpServletRequest peticion, HttpServletResponse respuesta, long inicio, String id,
                              Function<HttpServletRequest, String> plantillas)
            implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent evento) {
            registrar(peticion, respuesta, inicio, id, null, plantillas);
        }

        @Override
        public void onTimeout(AsyncEvent evento) {
        }

        @Override
        public void onError(AsyncEvent evento) {
        }

        @Override
        public void onStartAsync(AsyncEvent evento) {
            evento.getAsyncContext().addListener(this);
        }
    }
}
