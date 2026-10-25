package com.marketplace.auth_service.auditoria.domain.model;

import com.marketplace.auth_service.auditoria.domain.exception.AccionNoCatalogadaException;
import com.marketplace.auth_service.auditoria.domain.exception.DetalleInseguroException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistroAuditoriaTest {
    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID REGISTRO = UUID.randomUUID();
    private static final UUID CUENTA = UUID.randomUUID();

    private static RegistroAuditoria nuevo(String accion, Map<String, String> detalle) {
        return RegistroAuditoria.nuevo(REGISTRO, accion, Resultado.EXITO, ActorTipo.USUARIO,
                "sub-123", CUENTA, AHORA, "10.0.0.1", "agent", detalle);
    }

    @Test
    void catalogoCerradoConCriticidadDeSpec12() {
        assertThat(AccionAuditable.values()).hasSize(32);
        assertThat(AccionAuditable.ROL_ASIGNADO.esCritica()).isTrue();
        assertThat(AccionAuditable.CONTRASENA_CAMBIADA.esCritica()).isTrue();
        assertThat(AccionAuditable.MODULO_OBTUVO_DOCUMENTO.esCritica()).isTrue();
        assertThat(AccionAuditable.MODULO_CONSULTO_DIRECCIONES.esCritica()).isFalse();
        assertThat(AccionAuditable.SESION_INICIADA.esCritica()).isFalse();
        assertThat(AccionAuditable.SESION_FALLIDA.esCritica()).isFalse();
        assertThat(AccionAuditable.AUDITORIA_CONSULTADA.esCritica()).isFalse();
    }

    @Test
    void codigoFueraDeCatalogoSeRechaza() {
        assertThatThrownBy(() -> nuevo("ROL_SUPERADMIN", null))
                .isInstanceOf(AccionNoCatalogadaException.class);
        assertThatThrownBy(() -> nuevo(null, null))
                .isInstanceOf(AccionNoCatalogadaException.class);
    }

    @Test
    void detalleConSecretoSeRechaza() {
        assertThatThrownBy(() -> nuevo("CONTRASENA_CAMBIADA", Map.of("contrasena", "x")))
                .isInstanceOf(DetalleInseguroException.class);
        assertThatThrownBy(() -> nuevo("OTP_VERIFICADO", Map.of("codigoOtp", "123456")))
                .isInstanceOf(DetalleInseguroException.class);
        assertThatThrownBy(() -> nuevo("SESION_INICIADA", Map.of("token", "eyJhbGciOi")))
                .isInstanceOf(DetalleInseguroException.class);
        assertThatThrownBy(() -> nuevo("MODULO_OBTUVO_DOCUMENTO", Map.of("documento", "12345678")))
                .isInstanceOf(DetalleInseguroException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"contrasenaNueva", "nueva_contrasena", "refreshToken", "access_token", "hashRefresco",
            "codigoOtp2", "numeroDocumento", "client_secret", "Authorization", "codigo", "clave"})
    void claveQueContieneUnSecretoSeRechazaAunqueNoSeaExacta(String clave) {
        assertThatThrownBy(() -> nuevo("SESION_INICIADA", Map.of(clave, "valor-del-productor")))
                .isInstanceOf(DetalleInseguroException.class)
                .hasMessageNotContaining("valor-del-productor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.firma", "Bearer eyJhbGciOi.eyJzdWIi.x",
            "$2a$10$abcdefghijklmnopqrstuv", "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"})
    void valorConFormaDeJwtOHashSeRechazaEnCualquierClave(String valor) {
        assertThatThrownBy(() -> nuevo("SESION_CERRADA", Map.of("motivo", valor)))
                .isInstanceOf(DetalleInseguroException.class)
                .hasMessageNotContaining(valor);
    }

    @Test
    void referenciasPermitidasPorEsc123NoSeConfundenConSecretos() {
        var registro = nuevo("SESION_CERRADA", Map.of("token_jti", "2b7c0e51", "tipoDocumento", "DNI",
                "motivo", "logout", "rol", "VENDEDOR"));
        assertThat(registro.detalle()).containsKeys("token_jti", "tipoDocumento", "motivo", "rol");
    }

    @Test
    void detalleSeguroSegunEsc123SeAcepta() {
        var porJti = nuevo("SESION_CERRADA", Map.of("jti", "2b7c0e51"));
        assertThat(porJti.detalle()).containsEntry("jti", "2b7c0e51");
        var documento = nuevo("MODULO_OBTUVO_DOCUMENTO",
                Map.of("entregado", "EN_CLARO", "modulo", "despacho"));
        assertThat(documento.detalle()).containsEntry("modulo", "despacho");
        var sinDetalle = nuevo("SESION_FALLIDA", null);
        assertThat(sinDetalle.detalle()).isEmpty();
    }

    @Test
    void registroIdDistingueReintentosYDeduplica() {
        var primero = nuevo("ROL_ASIGNADO", Map.of("rol", "VENDEDOR"));
        var reintento = new RegistroAuditoria(REGISTRO, AccionAuditable.ROL_ASIGNADO,
                Resultado.EXITO, ActorTipo.USUARIO, "sub-123", CUENTA, AHORA, "10.0.0.1",
                "agent", Map.of("rol", "VENDEDOR"));
        assertThat(reintento.registroId()).isEqualTo(primero.registroId());
        assertThat(reintento).isEqualTo(primero);
    }

    @Test
    void toStringNoExponeDetalleNiActor() {
        var registro = nuevo("CUENTA_BLOQUEADA", Map.of("motivo", "manual"));
        assertThat(registro.toString()).doesNotContain("manual", "sub-123");
    }
}
