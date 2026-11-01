package com.marketplace.auth_service.perfil.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.ResultadoSolicitudCorreo;
import com.marketplace.auth_service.perfil.application.dto.SolicitudCambioCorreo;
import com.marketplace.auth_service.perfil.domain.exception.CambioCorreoNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FEAT-04-T3 sin Spring ni base de datos: titularidad, contraseña, correo libre, orden y efectos. */
class CambioCorreoServiceTest {
    private static final ContextoPeticion CTX = new ContextoPeticion("203.0.113.7", "JUnit", UUID.randomUUID());
    private final List<String> orden = new ArrayList<>();
    private final UUID titular = UUID.randomUUID();
    private final DoblesCambioCorreo.Credenciales credenciales = new DoblesCambioCorreo.Credenciales(orden);
    private final DoblesCambioCorreo.Cuentas cuentas = new DoblesCambioCorreo.Cuentas(orden, titular);
    private final DoblesCambioCorreo.Notificaciones notificaciones = new DoblesCambioCorreo.Notificaciones(orden);
    private final DoblesCambioCorreo.Efectos efectos = new DoblesCambioCorreo.Efectos(orden);
    private final CambioCorreoService servicio =
            new CambioCorreoService(credenciales, cuentas, notificaciones, efectos);

    private ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of("CLIENTE"), Set.of());
    }

    private ResultadoSolicitudCorreo solicitar(String nuevo) {
        return servicio.solicitar(actor(titular), titular, new SolicitudCambioCorreo(nuevo, "Clave-actual-1"), CTX);
    }

    @Test
    void esc163EnviaElEnlaceALaDireccionNuevaYUnAvisoALaAnterior() {
        assertThat(solicitar("  Nuevo@Correo.COM ")).isEqualTo(ResultadoSolicitudCorreo.ENVIADA);

        assertThat(notificaciones.confirmaciones).containsExactly("nuevo@correo.com");
        assertThat(notificaciones.avisos).containsExactly("actual@correo.com->nuevo@correo.com");
        assertThat(cuentas.correoActual).as("el correo no cambia hasta confirmar").isEqualTo("actual@correo.com");
    }

    @Test
    void laContrasenaSeComparaFueraDeLaTransaccionYLaCuentaSeBloqueaAntesDeConfirmarLaRevision() {
        solicitar("nuevo@correo.com");

        assertThat(orden).containsExactly("preverificar", "tx", "bloquear", "confirmarRevision",
                "confirmacion", "aviso");
    }

    @Test
    void esc164ConLaContrasenaEquivocadaNoSeEnviaNingunEnlace() {
        credenciales.contrasenaCorrecta = false;

        assertThat(solicitar("nuevo@correo.com")).isEqualTo(ResultadoSolicitudCorreo.CONTRASENA_INCORRECTA);

        assertThat(notificaciones.confirmaciones).isEmpty();
        assertThat(notificaciones.avisos).isEmpty();
        assertThat(orden).doesNotContain("tx", "bloquear");
    }

    @Test
    void siLaCredencialCambioEntrePreverificarYBloquearSeRechazaSinEnviarNada() {
        credenciales.revisionVigente = false;

        assertThat(solicitar("nuevo@correo.com")).isEqualTo(ResultadoSolicitudCorreo.CONTRASENA_INCORRECTA);

        assertThat(notificaciones.confirmaciones).isEmpty();
        assertThat(notificaciones.avisos).isEmpty();
    }

    @Test
    void unCorreoYaRegistradoNoSeEnviaNiAvisa() {
        cuentas.enUso.add("ocupado@correo.com");

        assertThat(solicitar("Ocupado@correo.com")).isEqualTo(ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE);

        assertThat(notificaciones.confirmaciones).isEmpty();
        assertThat(notificaciones.avisos).isEmpty();
    }

    @Test
    void elMismoCorreoQueElActualTampocoEstaDisponible() {
        assertThat(solicitar("ACTUAL@correo.com")).isEqualTo(ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE);

        assertThat(notificaciones.confirmaciones).isEmpty();
    }

    @Test
    void esc165OtraCuentaNoPuedeCambiarElCorreoAjenoYNoSeVerificaNingunaContrasena() {
        assertThatThrownBy(() -> servicio.solicitar(actor(UUID.randomUUID()), titular,
                new SolicitudCambioCorreo("nuevo@correo.com", "Clave-actual-1"), CTX))
                .isInstanceOf(CambioCorreoNoPermitidaException.class);

        assertThat(orden).isEmpty();
    }

    @Test
    void sinActorAutenticadoSeRechaza() {
        assertThatThrownBy(() -> servicio.solicitar(null, titular,
                new SolicitudCambioCorreo("nuevo@correo.com", "Clave-actual-1"), CTX))
                .isInstanceOf(CambioCorreoNoPermitidaException.class);
    }

    @Test
    void unaCuentaQueYaNoExisteRecibeElMismoRechazoQueUnaAjena() {
        cuentas.existe = false;

        assertThatThrownBy(() -> solicitar("nuevo@correo.com"))
                .isInstanceOf(CambioCorreoNoPermitidaException.class);
        assertThat(orden).doesNotContain("preverificar");
    }

    @Test
    void laSolicitudNoImprimeLaContrasenaNiElCorreo() {
        String texto = new SolicitudCambioCorreo("nuevo@correo.com", "Clave-actual-1").toString();

        assertThat(texto).doesNotContain("Clave-actual-1").doesNotContain("nuevo@correo.com");
    }
}
