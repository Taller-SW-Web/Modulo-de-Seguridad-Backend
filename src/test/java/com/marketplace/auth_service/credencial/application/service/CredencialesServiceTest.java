package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.domain.model.*;
import com.marketplace.auth_service.shared.domain.Rol;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredencialesServiceTest {
    final UUID id = UUID.randomUUID();
    final Instant cambio = Instant.parse("2026-01-01T00:00:00Z");
    final PersistenciaCredencialesPort persistencia = mock(PersistenciaCredencialesPort.class);
    final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    final PoliticaPasswordService politica = mock(PoliticaPasswordService.class);
    final HashContrasena hash = new HashContrasena("$2b$12$" + "a".repeat(53), AlgoritmoHash.BCRYPT);
    final HashContrasena equivalente = new HashContrasena("$2b$12$" + "b".repeat(53), AlgoritmoHash.BCRYPT);
    final CredencialesService servicio = new CredencialesService(persistencia, hasher, equivalente, politica);
    @BeforeEach void preparar() {
        when(persistencia.leer(id)).thenReturn(Optional.of(new Credencial(id, id, hash, cambio, 7)));
    }
    @Test void entregaRevisionRealYSinNormalizarSecreto() {
        String valor = "  Éxacta1!  ";
        when(hasher.verificar(valor, hash)).thenReturn(true);
        var resultado = servicio.preverificar(Optional.of(id), new SecretoEfimero(valor));
        assertThat(resultado.coincide()).isTrue();
        assertThat(resultado.revision()).contains(new RevisionCredencial(id, id, 7));
        verify(hasher).verificar(valor, hash);
        assertThat(resultado.toString()).doesNotContain(hash.valor(), valor);
        assertThat(new SecretoEfimero(valor).toString()).doesNotContain(valor);
    }
    @Test void noCoincideConservaRevisionParaRevalidarAntesDeRegistrarFallo() {
        var resultado = servicio.preverificar(Optional.of(id), new SecretoEfimero("incorrecta"));
        assertThat(resultado.coincide()).isFalse();
        assertThat(resultado.revision()).contains(new RevisionCredencial(id, id, 7));
    }
    @Test void inexistenteComparaEquivalentePeroNuncaCoincide() {
        when(hasher.verificar("intento", equivalente)).thenReturn(true);
        var resultado = servicio.preverificar(Optional.empty(), new SecretoEfimero("intento"));
        assertThat(resultado.coincide()).isFalse();
        assertThat(resultado.revision()).isEmpty();
        verify(hasher).verificar("intento", equivalente);
        verifyNoInteractions(persistencia);
    }
    @Test void usuarioSinCredencialTambienComparaEquivalente() {
        UUID desconocido = UUID.randomUUID();
        when(persistencia.leer(desconocido)).thenReturn(Optional.empty());
        assertThat(servicio.preverificar(Optional.of(desconocido), new SecretoEfimero("intento")).coincide()).isFalse();
        verify(hasher).verificar("intento", equivalente);
    }
    @Test void hashMalformadoSeRechazaSinPropagarDatos() {
        when(hasher.verificar("intento", hash)).thenThrow(new IllegalArgumentException("formato"));
        assertThat(servicio.preverificar(Optional.of(id), new SecretoEfimero("intento")).coincide()).isFalse();
        verify(hasher).verificar("intento", equivalente);
    }
    @ParameterizedTest @EnumSource(Rol.class)
    void caducidadPorCualquierRolDeGestionEnElLimite(Rol rol) {
        Instant limite = cambio.plus(Duration.ofDays(90));
        assertThat(servicio.evaluarCaducidad(id, Set.of(rol), limite.minusNanos(1))).isFalse();
        assertThat(servicio.evaluarCaducidad(id, Set.of(rol), limite)).isEqualTo(rol.esDeGestion());
        assertThat(servicio.evaluarCaducidad(id, Set.of(rol), limite.plusNanos(1))).isEqualTo(rol.esDeGestion());
    }
    @Test void rolClienteNoOcultaGestionYRolesVaciosNoCaducan() {
        Instant limite = cambio.plus(Duration.ofDays(90));
        assertThat(servicio.evaluarCaducidad(id, Set.of(Rol.CLIENTE, Rol.GESTOR_DESPACHO), limite)).isTrue();
        assertThat(servicio.evaluarCaducidad(id, Set.of(), limite)).isFalse();
    }
    @Test void delegaConfirmacionYPoliticaSinExponerPersistencia() {
        var revision = new RevisionCredencial(id, id, 7);
        when(persistencia.coincideRevision(revision)).thenReturn(true);
        assertThat(servicio.confirmarRevision(revision)).isTrue();
        var secreto = new SecretoEfimero("Nueva8!abcd");
        var resultado = new ValidacionPassword(List.of());
        when(politica.validar(id, secreto)).thenReturn(resultado);
        assertThat(servicio.validarNueva(id, secreto)).isSameAs(resultado);
    }
}
