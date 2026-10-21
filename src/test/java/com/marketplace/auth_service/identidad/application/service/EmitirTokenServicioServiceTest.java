package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.marketplace.auth_service.identidad.application.dto.ResultadoTokenServicio;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.dto.SolicitudTokenServicio;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.ScopeConcedido;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** POST /auth/token: autenticación del cliente y scopes concedidos (SPEC-17 RF-17.4/17.5/17.12, ESC-17.3). */
class EmitirTokenServicioServiceTest {

    private static final String ISSUER = "https://g7.example.test/api/v1/auth";
    private static final String SECRETO = "secreto-de-prueba-con-mucha-entropia-0123456789";

    private final Map<String, ClienteServicio> clientes = new HashMap<>();
    private final FirmarAccesoService firma = new FirmarAccesoService(
            new NimbusFirmadorJws(ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072))),
            ISSUER, Clock.systemUTC());
    private final EmitirTokenServicioService servicio =
            new EmitirTokenServicioService(id -> Optional.ofNullable(clientes.get(id)), firma);

    private void registrar(String clientId, boolean activo, ScopeConcedido... scopes) {
        clientes.put(clientId, new ClienteServicio(clientId, SecretoCliente.hash(SECRETO), activo, List.of(scopes)));
    }

    private ResultadoTokenServicio pedir(String clientId, String secreto, String scope) {
        return servicio.emitir(new SolicitudTokenServicio("client_credentials", clientId, secreto, scope));
    }

    @Test
    void concedeExactamenteLosScopesAsignadosAunquePidaMas() throws Exception {
        registrar("modulo-despacho", true, new ScopeConcedido("usuarios:leer", "api-seguridad"),
                new ScopeConcedido("direcciones:leer", "api-seguridad"));

        var resultado = pedir("modulo-despacho", SECRETO, "usuarios:leer direcciones:leer usuarios:leer:documento");

        assertThat(resultado).isInstanceOfSatisfying(ResultadoTokenServicio.Emitido.class, emitido -> {
            assertThat(emitido.scopes()).containsExactly("direcciones:leer", "usuarios:leer");
            ServicioValidado validado = firma.validarServicio(emitido.token().token());
            assertThat(validado.clientId()).isEqualTo("modulo-despacho");
            assertThat(validado.scopes()).containsExactlyInAnyOrder("direcciones:leer", "usuarios:leer");
        });
    }

    @Test
    void laAudienciaEsLaListaSinRepetirDeLasApisDuenas() throws Exception {
        registrar("modulo-marketplace", true, new ScopeConcedido("cotizaciones:calcular", "api-despacho"),
                new ScopeConcedido("seguimientos:leer", "api-despacho"),
                new ScopeConcedido("usuarios:leer", "api-seguridad"));

        var emitido = (ResultadoTokenServicio.Emitido) pedir("modulo-marketplace", SECRETO, null);

        var claims = SignedJWT.parse(emitido.token().token()).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("api-despacho", "api-seguridad");
        assertThat(claims.getClaim("scope")).isEqualTo("cotizaciones:calcular seguimientos:leer usuarios:leer");
    }

    @Test
    void clienteInexistenteInactivoOSecretoIncorrectoRespondenIgual() {
        registrar("modulo-ventas", true, new ScopeConcedido("usuarios:leer", "api-seguridad"));
        registrar("modulo-retail", false, new ScopeConcedido("usuarios:leer", "api-seguridad"));

        assertThat(pedir("modulo-fantasma", SECRETO, null)).isInstanceOf(ResultadoTokenServicio.ClienteInvalido.class);
        assertThat(pedir("modulo-retail", SECRETO, null)).isInstanceOf(ResultadoTokenServicio.ClienteInvalido.class);
        assertThat(pedir("modulo-ventas", SECRETO + "x", null)).isInstanceOf(ResultadoTokenServicio.ClienteInvalido.class);
        assertThat(pedir("modulo-ventas", "", null)).isInstanceOf(ResultadoTokenServicio.ClienteInvalido.class);
        assertThat(pedir(null, SECRETO, null)).isInstanceOf(ResultadoTokenServicio.ClienteInvalido.class);
    }

    @Test
    void soloSeAdmiteClientCredentials() {
        registrar("modulo-ventas", true, new ScopeConcedido("usuarios:leer", "api-seguridad"));

        assertThat(servicio.emitir(new SolicitudTokenServicio("password", "modulo-ventas", SECRETO, null)))
                .isEqualTo(new ResultadoTokenServicio.Invalida("grant_type", "Solo se admite client_credentials."));
        assertThat(servicio.emitir(new SolicitudTokenServicio(null, "modulo-ventas", SECRETO, null)))
                .isEqualTo(new ResultadoTokenServicio.Invalida("grant_type", "Es obligatorio."));
    }

    @Test
    void elSecretoNuncaApareceEnTextos() {
        var solicitud = new SolicitudTokenServicio("client_credentials", "modulo-ventas", SECRETO, null);
        registrar("modulo-ventas", true);

        assertThat(solicitud.toString()).doesNotContain(SECRETO);
        assertThat(clientes.get("modulo-ventas").toString()).doesNotContain(SecretoCliente.hash(SECRETO));
        assertThat(SecretoCliente.coincide(SECRETO, SecretoCliente.hash(SECRETO).toUpperCase())).isTrue();
        assertThat(SecretoCliente.hash(SECRETO)).hasSize(64).doesNotContain(SECRETO);
    }
}
