package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.domain.model.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PoliticaPasswordServiceTest {
    final UUID id = UUID.randomUUID();
    final PasswordComunesPort comunes = mock(PasswordComunesPort.class);
    final DatosPersonalesPasswordPort personas = mock(DatosPersonalesPasswordPort.class);
    final PersistenciaCredencialesPort persistencia = mock(PersistenciaCredencialesPort.class);
    final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    final PoliticaPasswordService politica = new PoliticaPasswordService(comunes, personas, persistencia,
            new PasswordHistoryChecker(hasher));

    @BeforeEach void preparar() {
        when(personas.leer(id)).thenReturn(new DatosPersonalesPassword("María José", "García Ruiz", "mjgarcia@example.test"));
        when(persistencia.leer(id)).thenReturn(Optional.empty());
    }
    ValidacionPassword validar(String secreto) { return politica.validar(id, new SecretoEfimero(secreto)); }
    @Test void aceptaNuevaSinExigirRequisitosAdicionales() {
        assertThat(validar("Zq8!ryTuvX")).isEqualTo(new ValidacionPassword(List.of()));
        assertThat(politica.consultar()).isEqualTo(PoliticaPassword.vigente());
    }
    @ParameterizedTest @CsvSource({
        "Aa1!,LONGITUD_MINIMA", "abcdefgh1!,MAYUSCULA", "ABCDEFGH1!,MINUSCULA",
        "Abcdefghij!,DIGITO", "Abcdefghi1,CARACTER_ESPECIAL"})
    void informaReglaCanonica(String secreto, ReglaPassword regla) {
        assertThat(validar(secreto).errores()).extracting(InfraccionPassword::regla).contains(regla);
    }
    @Test void acumulaReglasYLongitudEsperada() {
        assertThat(validar("a").errores()).contains(new InfraccionPassword(ReglaPassword.LONGITUD_MINIMA, 10),
                new InfraccionPassword(ReglaPassword.MAYUSCULA, null),
                new InfraccionPassword(ReglaPassword.DIGITO, null),
                new InfraccionPassword(ReglaPassword.CARACTER_ESPECIAL, null));
    }
    @ParameterizedTest @CsvSource({"'xxMÁRÍA  JÓSÉ1!xx'","'RuizClave9!xx'","'xxmjgarcia@example.test1!'","'xxmjgarcia9!AA'"})
    void protegePersonalesEnCopiasNormalizadas(String secreto) {
        assertThat(validar(secreto).errores()).extracting(InfraccionPassword::regla)
                .contains(ReglaPassword.DATOS_PERSONALES);
    }
    @Test void protegeNombresCompletosCortos() {
        when(personas.leer(id)).thenReturn(new DatosPersonalesPassword("Li", "Wu", "otro@example.test"));
        assertThat(validar("Z9!xxLIxxxx").errores()).extracting(InfraccionPassword::regla)
                .contains(ReglaPassword.DATOS_PERSONALES);
    }
    @Test void comparaComunesNormalizadasSinCambiarSecretoDeHistorial() {
        String secreto = "  Zq8!RýTuvX  ";
        when(comunes.contiene("zq8!rytuvx")).thenReturn(true);
        var hash = new HashContrasena("$2b$12$" + "a".repeat(53), AlgoritmoHash.BCRYPT);
        var actual = new Credencial(id, id, hash, Instant.EPOCH, 0);
        when(persistencia.leer(id)).thenReturn(Optional.of(actual));
        when(persistencia.historial(id, actual)).thenReturn(new PasswordHistorial(List.of()));
        when(hasher.verificar(secreto, hash)).thenReturn(true);
        assertThat(validar(secreto).errores()).extracting(InfraccionPassword::regla)
                .contains(ReglaPassword.CONTRASENA_COMUN, ReglaPassword.YA_UTILIZADA);
        verify(hasher).verificar(secreto, hash);
    }
    @Test void historialAnteriorSeVerificaCriptograficamente() {
        var actualHash = new HashContrasena("$2b$12$" + "a".repeat(53), AlgoritmoHash.BCRYPT);
        var anteriorHash = new HashContrasena("$2b$12$" + "b".repeat(53), AlgoritmoHash.BCRYPT);
        var actual = new Credencial(id, id, actualHash, Instant.EPOCH, 0);
        when(persistencia.leer(id)).thenReturn(Optional.of(actual));
        when(persistencia.historial(id, actual)).thenReturn(new PasswordHistorial(List.of(
                new PasswordHistorial.Entrada(UUID.randomUUID(), anteriorHash, Instant.EPOCH))));
        when(hasher.verificar("Zq8!ryTuvX", anteriorHash)).thenReturn(true);
        assertThat(validar("Zq8!ryTuvX").errores()).extracting(InfraccionPassword::regla)
                .contains(ReglaPassword.YA_UTILIZADA);
    }
}
