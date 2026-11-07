package com.marketplace.auth_service.perfil.application.service;

import static com.marketplace.auth_service.perfil.application.service.DoblesPerfil.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
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

    private static EditarPerfilPropio edicion(DoblesPerfil d) {
        ConsultarPerfil consulta = new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> "x"));
        return new EditarPerfilPropio(d, consulta);
    }

    @Test
    void parcialConservaOmitidosYBloqueaAntesDeEscribir() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(pr("Eva Lucia"), om(), om()));

        assertThat(d.escrituras).containsExactly("bloquear:" + A, "actualizar:" + A);
        assertThat(p.nombres()).isEqualTo("Eva Lucia");
        assertThat(p.apellidos()).isEqualTo("Perez");
        assertThat(p.celular()).isEqualTo("+51987654321");
        assertThat(d.cambiosRecibidos.get(0).apellidos().presente()).isFalse();
    }

    @Test
    void recortaEspaciosAntesDeEnviarAEva() {
        DoblesPerfil d = dobles();

        edicion(d).actualizarPropio(actor(A), cambio(pr("  Ana Maria  "), pr(" Gomez "), om()));

        assertThat(d.cambiosRecibidos.get(0).nombres().valor()).isEqualTo("Ana Maria");
        assertThat(d.cambiosRecibidos.get(0).apellidos().valor()).isEqualTo("Gomez");
    }

    @Test
    void celularNuevoQuedaNoVerificadoEnLaRespuesta() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("+51900000000")));

        assertThat(p.celular()).isEqualTo("+51900000000");
        assertThat(p.celularVerificado()).isFalse();
    }

    @Test
    void escribeSoloSobreLaCuentaDelActor() {
        DoblesPerfil d = dobles();
        edicion(d).actualizarPropio(actor(A), cambio(pr("Ana"), om(), om()));
        assertThat(d.escrituras).containsExactly("bloquear:" + A, "actualizar:" + A);
    }

    @Test
    void sinCambiosNoEscribeYDevuelveElPerfilActual() {
        DoblesPerfil d = dobles();

        PerfilProyectado p = edicion(d).actualizarPropio(actor(A), cambio(om(), om(), om()));

        assertThat(d.escrituras).hasSize(0);
        assertThat(p.nombres()).isEqualTo("Ana");
    }

    @Test
    void nombreVacioONuloNoEscribeNada() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("   "), om(), om())))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), pr(null), om())))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void nombreDeMasDe100CaracteresSeRechaza() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("a".repeat(101)), om(), om())))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void celularConFormatoInvalidoONuloSeRechaza() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("987654321"))))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("+5198765432"))))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr(null))))
                .isInstanceOf(AtributoInvalidoException.class);
        assertThat(d.escrituras).hasSize(0);
    }

    @Test
    void campoValidoMasInvalidoNoAplicaLaParteValida() {
        DoblesPerfil d = dobles();

        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(pr("Eva"), om(), pr("123"))))
                .isInstanceOf(AtributoInvalidoException.class);

        assertThat(d.escrituras).hasSize(0);
        assertThat(d.cuentas.get(A).nombres()).isEqualTo("Ana");
    }

    @Test
    void elMensajeDeErrorNoIncluyeElValorRecibido() {
        DoblesPerfil d = dobles();
        assertThatThrownBy(() -> edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("TELEFONO-SECRETO"))))
                .isInstanceOf(AtributoInvalidoException.class);
        try {
            edicion(d).actualizarPropio(actor(A), cambio(om(), om(), pr("TELEFONO-SECRETO")));
        } catch (AtributoInvalidoException e) {
            assertThat(e.getMessage()).doesNotContain("TELEFONO-SECRETO");
            assertThat(e.campo()).isEqualTo("celular");
        }
    }

    // --- Efectos (EP-08-T007): evento y auditoría con nombres de campo, jamás valores ---

    private static final class EfectosGrabados implements com.marketplace.auth_service.perfil.application.port.out.EfectosPerfilPort {
        final List<String> llamadas = new ArrayList<>();

        @Override
        public void atributosActualizados(UUID actorId, UUID usuarioId, List<String> campos) {
            llamadas.add(actorId + "->" + usuarioId + ":" + String.join(",", campos));
        }
    }

    private static EditarPerfilPropio conEfectos(DoblesPerfil d, EfectosGrabados efectos) {
        ConsultarPerfil consulta = new ConsultarPerfil(d, d, d, d, d, new ProyectorDocumento(documento -> "x"));
        return new EditarPerfilPropio(d, consulta, efectos);
    }

    @Test
    void publicaSoloLosCamposQueRealmenteCambiaron() {
        DoblesPerfil d = dobles();
        EfectosGrabados efectos = new EfectosGrabados();

        conEfectos(d, efectos).actualizarPropio(actor(A), cambio(pr("Eva"), pr("Perez"), pr("+51900000000")));

        assertThat(efectos.llamadas).containsExactly(A + "->" + A + ":nombres,celular");
    }

    @Test
    void unValorIgualAlActualNoGeneraEvento() {
        DoblesPerfil d = dobles();
        EfectosGrabados efectos = new EfectosGrabados();

        conEfectos(d, efectos).actualizarPropio(actor(A), cambio(pr("Ana"), om(), om()));

        assertThat(efectos.llamadas).isEmpty();
    }

    @Test
    void unInvalidoNoGeneraEventoNiEscribe() {
        DoblesPerfil d = dobles();
        EfectosGrabados efectos = new EfectosGrabados();

        assertThatThrownBy(() -> conEfectos(d, efectos).actualizarPropio(actor(A), cambio(pr("Eva"), om(), pr("1"))))
                .isInstanceOf(AtributoInvalidoException.class);

        assertThat(efectos.llamadas).isEmpty();
        assertThat(d.escrituras).isEmpty();
    }

    @Test
    void sinCambiosNoGeneraEvento() {
        DoblesPerfil d = dobles();
        EfectosGrabados efectos = new EfectosGrabados();

        conEfectos(d, efectos).actualizarPropio(actor(A), cambio(om(), om(), om()));

        assertThat(efectos.llamadas).isEmpty();
    }
}
