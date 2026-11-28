package com.marketplace.auth_service.perfil.application.service;

import static com.marketplace.auth_service.perfil.application.service.DoblesPerfil.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.out.AuditoriaPerfilPort;
import com.marketplace.auth_service.perfil.application.port.out.EventosPerfilPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditarPerfilPropioTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static CambioAtributos cambio(CampoEditable<String> n, CampoEditable<String> a, CampoEditable<String> c) {
        return new CambioAtributos(n, a, c);
    }

    private static CampoEditable<String> om() {
        return CampoEditable.omitido();
    }

    private static CampoEditable<String> pr(String v) {
        return CampoEditable.presente(v);
    }

    private static DoblesPerfil dobles() {
        DoblesPerfil d = new DoblesPerfil();
        d.cuenta(A, "Ana", "Perez", "+51987654321", Set.of("CLIENTE"), false);
        return d;
    }

    /** Evento y auditoría capturados: solo nombres de campos. */
    static final class Efectos implements EventosPerfilPort, AuditoriaPerfilPort {
        final List<String> eventos = new ArrayList<>();
        final List<String> auditorias = new ArrayList<>();

        @Override
        public void atributosActualizados(UUID usuarioId, List<String> campos) {
            eventos.add(usuarioId + ":" + String.join(",", campos));
        }

        @Override
        public void atributosActualizados(ActorUsuario actor, UUID usuarioId, List<String> campos,
                                          ContextoPeticion contexto) {
            auditorias.add("ADMIN " + actor.sub() + "->" + usuarioId + ":" + String.join(",", campos));
        }

        @Override
        public void atributosActualizadosPorTitular(ActorUsuario actor, List<String> campos,
                                                    ContextoPeticion contexto) {
            auditorias.add("PROPIA " + actor.sub() + ":" + String.join(",", campos));
        }
    }

    private static EditarPerfilPropio edicion(DoblesPerfil d) {
        return edicion(d, new Efectos());
    }

    private static EditarPerfilPropio edicion(DoblesPerfil d, Efectos e) {
        ConsultarPerfil consulta = new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> "x"));
        return new EditarPerfilPropio(d, consulta, e, e);
    }

    @Test
    void parcialConservaOmitidosYBloqueaAntesDeEscribir() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(pr("Eva Lucia"), om(), om()), null);

        assertThat(d.escrituras).containsExactly("bloquear:" + A, "actualizar:" + A);
        assertThat(p.nombres()).isEqualTo("Eva Lucia");
        assertThat(p.apellidos()).isEqualTo("Perez");
        assertThat(p.celular()).isEqualTo("+51987654321");
        assertThat(d.cambiosRecibidos.get(0).apellidos().presente()).isFalse();
    }

    @Test
    void recortaEspaciosAntesDeEnviarAEva() {
        DoblesPerfil d = dobles();

        edicion(d).actualizarPropio(actor(A), cambio(pr("  Ana Maria  "), pr(" Gomez "), om()), null);

        assertThat(d.cambiosRecibidos.get(0).nombres().valor()).isEqualTo("Ana Maria");
        assertThat(d.cambiosRecibidos.get(0).apellidos().valor()).isEqualTo("Gomez");
    }

    @Test
    void celularNuevoQuedaNoVerificadoEnLaRespuesta() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("+51900000000")), null);

        assertThat(p.celular()).isEqualTo("+51900000000");
        assertThat(p.celularVerificado()).isFalse();
    }

    @Test
    void escribeSoloSobreLaCuentaDelActor() {
        DoblesPerfil d = dobles();
        edicion(d).actualizarPropio(actor(A), cambio(pr("Ana"), om(), om()), null);
        assertThat(d.escrituras).containsExactly("bloquear:" + A, "actualizar:" + A);
    }

    @Test
    void sinCambiosNoEscribeYDevuelveElPerfilActual() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(om(), om(), om()), null);

        assertThat(d.escrituras).hasSize(0);
        assertThat(p.nombres()).isEqualTo("Ana");
    }

    @Test
    void nombreVacioONuloNoEscribeNada() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("   "), om(), om()), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), pr(null), om()), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void nombreDeMasDe100CaracteresSeRechaza() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("a".repeat(101)), om(), om()), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void celularConFormatoInvalidoONuloSeRechaza() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("987654321")), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("+5198765432")), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr(null)), null))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void campoValidoMasInvalidoNoAplicaLaParteValida() {
        DoblesPerfil d = dobles();

        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("Eva"), om(), pr("123")), null))
                .isInstanceOf(AtributoInvalidoException.class);

        assertThat(d.escrituras).hasSize(0);
        assertThat(d.cuentas.get(A).nombres()).isEqualTo("Ana");
    }

    @Test
    void elMensajeDeErrorNoIncluyeElValorRecibido() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("TELEFONO-SECRETO")), null))
                .isInstanceOf(AtributoInvalidoException.class);
        try {
            edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("TELEFONO-SECRETO")), null);
        } catch (AtributoInvalidoException e) {
            assertThat(e.getMessage()).doesNotContain("TELEFONO-SECRETO");
            assertThat(e.campo()).isEqualTo("celular");
        }
    }

    @Test
    void emiteEventoYAuditoriaSoloConLosCamposQueCambiaron() {
        DoblesPerfil d = dobles();
        Efectos e = new Efectos();

        // apellidos igual al actual: no cuenta como cambio
        edicion(d, e).actualizarPropio(actor(A), cambio(pr("Eva"), pr("Perez"), pr("+51900000000")), null);

        assertThat(e.eventos).containsExactly(A + ":nombres,celular");
        assertThat(e.auditorias).containsExactly("PROPIA " + A + ":nombres,celular");
    }

    @Test
    void mismoValorQueElActualNoEmite() {
        DoblesPerfil d = dobles();
        Efectos e = new Efectos();

        edicion(d, e).actualizarPropio(actor(A), cambio(pr("  Ana "), om(), om()), null);

        assertThat(e.eventos).isEmpty();
        assertThat(e.auditorias).isEmpty();
    }

    @Test
    void sinCambiosNoEmite() {
        DoblesPerfil d = dobles();
        Efectos e = new Efectos();

        edicion(d, e).actualizarPropio(actor(A), cambio(om(), om(), om()), null);

        assertThat(e.eventos).isEmpty();
        assertThat(e.auditorias).isEmpty();
    }

    @Test
    void invalidoNoEscribeNiEmite() {
        DoblesPerfil d = dobles();
        Efectos e = new Efectos();

        assertThatThrownBy(() -> edicion(d, e).actualizarPropio(actor(A), cambio(pr("Eva"), om(), pr("123")), null))
                .isInstanceOf(AtributoInvalidoException.class);

        assertThat(d.escrituras).isEmpty();
        assertThat(e.eventos).isEmpty();
        assertThat(e.auditorias).isEmpty();
    }

    @Test
    void elEventoNoLlevaValores() {
        DoblesPerfil d = dobles();
        Efectos e = new Efectos();

        edicion(d, e).actualizarPropio(actor(A), cambio(pr("ValorSecreto"), om(), om()), null);

        assertThat(String.join("|", e.eventos) + String.join("|", e.auditorias)).doesNotContain("ValorSecreto");
    }
}
