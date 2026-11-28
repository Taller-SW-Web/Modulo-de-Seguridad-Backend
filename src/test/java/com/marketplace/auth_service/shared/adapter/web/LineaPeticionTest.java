package com.marketplace.auth_service.shared.adapter.web;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.marketplace.auth_service.shared.adapter.web.LineaPeticion.Responsable.CLIENTE;
import static com.marketplace.auth_service.shared.adapter.web.LineaPeticion.Responsable.NOSOTROS;
import static com.marketplace.auth_service.shared.adapter.web.LineaPeticion.Responsable.OK;
import static org.assertj.core.api.Assertions.assertThat;

/** Registro de peticiones (docs/operacion/LOGS.md): cómo se arma cada parte de la línea. */
class LineaPeticionTest {

    @Test
    void laLineaSeLeeDeIzquierdaADerecha() {
        var linea = new LineaPeticion("modulo-despacho", "GET", "/api/v1/usuarios/{id}", 403, "SCOPE_INSUFICIENTE",
                "el token es válido pero le falta el scope o permiso que exige esta ruta", 11, "9b21f0c4a1d2e3f4",
                CLIENTE);

        assertThat(linea.texto()).isEqualTo("modulo-despacho | GET /api/v1/usuarios/{id} | 403 SCOPE_INSUFICIENTE"
                + " | el token es válido pero le falta el scope o permiso que exige esta ruta | 11 ms"
                + " | id=9b21f0c4a1d2e3f4 | [CLIENTE]");
        assertThat(linea.campos()).containsEntry("responsable", "CLIENTE").containsEntry("estado", "403")
                .containsEntry("codigo", "SCOPE_INSUFICIENTE").containsEntry("lenta", "false");
    }

    @Test
    void unaPeticionLentaLoDice() {
        var linea = new LineaPeticion("anónimo", "POST", "/api/v1/auth/login", 200, null, "correcto", 2300, "a1b2c3d4e5f6a7b8", OK);

        assertThat(linea.texto()).contains("| 2300 ms LENTO |").contains("| 200 | correcto |");
        assertThat(linea.campos()).containsEntry("lenta", "true").containsEntry("codigo", "");
    }

    @Test
    void elResponsableSaleDelCodigoHttp() {
        assertThat(LineaPeticion.responsableDe(200)).isEqualTo(OK);
        assertThat(LineaPeticion.responsableDe(204)).isEqualTo(OK);
        assertThat(LineaPeticion.responsableDe(302)).isEqualTo(OK);
        for (int estado : new int[]{400, 401, 403, 404, 409, 410, 422, 429}) {
            assertThat(LineaPeticion.responsableDe(estado)).as("%s", estado).isEqualTo(CLIENTE);
        }
        assertThat(LineaPeticion.responsableDe(500)).isEqualTo(NOSOTROS);
        assertThat(LineaPeticion.responsableDe(503)).isEqualTo(NOSOTROS);
    }

    @Test
    void elMotivoNombraLosCamposPeroNuncaSusValoresNiLosMensajes() {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setProperty("code", "VALIDACION");
        problema.setProperty("errores", List.of(
                Map.of("campo", "correo", "mensaje", "ana@correo.test no es válido"),
                Map.of("campo", "celular", "mensaje", "999888777 no es válido")));

        String motivo = LineaPeticion.motivoDe(400, problema);

        assertThat(motivo).isEqualTo("el cuerpo no cumple el contrato: campos correo, celular");
        assertThat(motivo).doesNotContain("ana@correo.test").doesNotContain("999888777");
    }

    @Test
    void laPoliticaDeContrasenaNombraLasReglas() {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setProperty("code", "POLITICA_INCUMPLIDA");
        problema.setProperty("errores", List.of(Map.of("regla", "LONGITUD_MINIMA", "esperado", 12),
                Map.of("regla", "MAYUSCULA")));

        assertThat(LineaPeticion.motivoDe(422, problema))
                .isEqualTo("la contraseña no cumple la política: reglas LONGITUD_MINIMA, MAYUSCULA");
    }

    @Test
    void sinCodigoDelContratoSeExplicaPorElEstado() {
        assertThat(LineaPeticion.motivoDe(200, null)).isEqualTo("correcto");
        assertThat(LineaPeticion.motivoDe(204, null)).isEqualTo("correcto (sin contenido)");
        assertThat(LineaPeticion.motivoDe(404, null)).isEqualTo("la ruta no existe");
        assertThat(LineaPeticion.motivoDe(405, null)).isEqualTo("método HTTP no permitido en esta ruta");
        assertThat(LineaPeticion.motivoDe(500, null)).isEqualTo("error inesperado en nuestro servicio");
    }

    @Test
    void unCodigoFueraDelCatalogoUsaElTitulo() {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problema.setProperty("code", "CODIGO_NUEVO");
        problema.setTitle("Algo nuevo");

        assertThat(LineaPeticion.motivoDe(409, problema)).isEqualTo("Algo nuevo");
    }

    @Test
    void laRutaEsUnaPlantillaDeclaradaOUnaEtiquetaFija() {
        assertThat(LineaPeticion.rutaDe("/api/v1/usuarios/{id}")).isEqualTo("/api/v1/usuarios/{id}");
        // Sin plantilla nunca se copia la URL recibida: podría llevar un token o un secreto.
        assertThat(LineaPeticion.rutaDe(null)).isEqualTo(LineaPeticion.RUTA_NO_RECONOCIDA);
        assertThat(LineaPeticion.rutaDe("/**")).isEqualTo(LineaPeticion.RUTA_NO_RECONOCIDA);
        assertThat(LineaPeticion.rutaDe(" ")).isEqualTo(LineaPeticion.RUTA_NO_RECONOCIDA);
    }

    @Test
    void elNumeroDeSeguimientoAjenoSoloSeUsaSiEsSeguro() {
        assertThat(RegistroPeticiones.idDe("g1-pedido-000123")).isEqualTo("g1-pedido-000123");
        assertThat(RegistroPeticiones.idDe(UUID.randomUUID().toString())).hasSize(36);
        for (String inseguro : new String[]{null, "", "corto", "con espacio 123", "a|b|c|d|e|f", "x".repeat(65),
                "linea\nfalsa123"}) {
            assertThat(RegistroPeticiones.idDe(inseguro)).as("%s", inseguro).matches("[0-9a-f]{16}");
        }
    }

    @Test
    void elLlamanteEsElModuloOLaPersonaSinDatosPersonales() {
        var modulo = new TestingAuthenticationToken(new ActorServicio("modulo-despacho", UUID.randomUUID(),
                Set.of("usuarios:leer")), null);
        UUID sub = UUID.fromString("3f2a1b2c-0000-4000-8000-000000000001");
        var persona = new TestingAuthenticationToken(new ActorUsuario(sub, UUID.randomUUID(),
                Set.of("VENDEDOR", "CLIENTE"), Set.of()), null);

        assertThat(CapturaLlamante.describir(modulo)).isEqualTo("modulo-despacho");
        assertThat(CapturaLlamante.describir(persona)).isEqualTo("usuario 3f2a1b2c (CLIENTE,VENDEDOR)");
        assertThat(CapturaLlamante.describir(null)).isNull();
    }

    @Test
    void unTextoAjenoInseguroNoLlegaAlLog() {
        assertThat(LineaPeticion.limpiar("modulo-ventas")).isEqualTo("modulo-ventas");
        assertThat(LineaPeticion.limpiar("x | [NOSOTROS]")).isEqualTo("(valor no válido)");
        assertThat(LineaPeticion.limpiar(" ")).isNull();
    }
}
