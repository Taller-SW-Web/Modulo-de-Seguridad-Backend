package com.marketplace.auth_service.architecture;

import com.marketplace.auth_service.autorizacion.application.dto.fixture.FixtureDtos;
import com.marketplace.auth_service.autorizacion.application.port.in.fixture.FixturePuertos;
import com.marketplace.auth_service.autorizacion.application.port.out.fixture.SalidaPropia;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContratosExportadosRegresionTest {
    private void rechazado(ArchRule regla, String tipo, Class<?>... clases) {
        var resultado = regla.evaluate(new ClassFileImporter().importClasses(clases));
        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().toString()).contains(tipo);
    }
    private void aceptado(ArchRule regla, Class<?>... clases) {
        var resultado = regla.evaluate(new ClassFileImporter().importClasses(clases));
        assertThat(resultado.getFailureReport().toString()).as("violaciones inesperadas").satisfies(texto ->
                assertThat(resultado.hasViolation()).isFalse());
    }

    @Test void rechazaFormaDelPr5SinUnConsumidor() {
        rechazado(ContratosExportados.puertosDeEntrada(), "domain.fixture.ModeloInterno", FixturePuertos.Directo.class);
    }
    @Test void rechazaDominioDentroDeGenericosAnidados() {
        rechazado(ContratosExportados.puertosDeEntrada(), "domain.fixture.ModeloInterno", FixturePuertos.Generico.class);
    }
    @Test void rechazaParametroDeDominio() {
        rechazado(ContratosExportados.puertosDeEntrada(), "domain.fixture.ModeloInterno", FixturePuertos.Parametro.class);
    }
    @Test void rechazaArregloDeDominio() {
        rechazado(ContratosExportados.puertosDeEntrada(), "domain.fixture.ModeloInterno", FixturePuertos.Arreglo.class);
    }
    @Test void rechazaDominioEnTipoHeredado() {
        rechazado(ContratosExportados.puertosDeEntrada(), "domain.fixture.ModeloInterno", FixturePuertos.Heredado.class);
    }
    @Test void rechazaTipoDePersistencia() {
        rechazado(ContratosExportados.puertosDeEntrada(), "adapter.out.persistence.fixture.EntidadInterna", FixturePuertos.Persistencia.class);
    }
    @Test void rechazaPageDeSpringAunqueSuContenidoSeaDto() {
        rechazado(ContratosExportados.puertosDeEntrada(), "org.springframework.data.domain.Page", FixturePuertos.Spring.class);
    }
    @Test void dtoQueOcultaDominioTambienFalla() {
        rechazado(ContratosExportados.dtoDeAplicacion(), "domain.fixture.ModeloInterno", FixtureDtos.DominioAnidado.class);
    }
    @Test void campoPrivadoDelDtoNoOcultaElTipoInterno() {
        rechazado(ContratosExportados.dtoDeAplicacion(), "domain.fixture.ModeloInterno", FixtureDtos.CampoPrivado.class);
    }
    @Test void admiteDtoYContenedoresJava() {
        aceptado(ContratosExportados.puertosDeEntrada(), FixturePuertos.Valido.class);
        aceptado(ContratosExportados.dtoDeAplicacion(), FixtureDtos.Valido.class);
    }
    @Test void compruebaDtoAunqueSuPuertoUseElEnvoltorioCorrecto() {
        aceptado(ContratosExportados.puertosDeEntrada(), FixturePuertos.DtoConDominio.class);
        rechazado(ContratosExportados.dtoDeAplicacion(), "domain.fixture.ModeloInterno", FixtureDtos.DominioAnidado.class);
    }
    @Test void noAplicaLaRestriccionDeExportacionAPuertosDeSalida() {
        aceptado(ContratosExportados.puertosDeEntrada(), SalidaPropia.class);
        aceptado(ContratosExportados.dtoDeAplicacion(), SalidaPropia.class);
    }
}
