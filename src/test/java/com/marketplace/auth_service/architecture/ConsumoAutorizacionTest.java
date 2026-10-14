package com.marketplace.auth_service.architecture;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.fixture.PuenteAutorizacionDeEjemplo;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.fixture.PuenteConModeloInterno;
import com.marketplace.auth_service.sesion.application.service.fixture.ConsumoSinPuente;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autorización consumida desde otra capacidad (revisión de Luis en #5): un
 * puente de {@code sesion} usa {@code AutorizacionPort} y pasa la regla de
 * consumo entre capacidades <b>sin relajarla</b>. Para comprobar que la regla
 * muerde, un puente con el modelo interno y un consumo sin puente fallan.
 *
 * <p>Los consumidores son clases de ejemplo en {@code src/test}; la regla es
 * la misma de {@link ArquitecturaHexagonalTest#consumoEntreCapacidades()}.
 */
class ConsumoAutorizacionTest {

    private static EvaluationResult evaluar(Class<?>... consumidores) {
        JavaClasses clases = new ClassFileImporter().importClasses(Stream.concat(Stream.of(consumidores),
                Stream.of(AutorizacionPort.class, PermisosResueltos.class, Rol.class, Permiso.class))
                .toArray(Class<?>[]::new));
        return ArquitecturaHexagonalTest.consumoEntreCapacidades().evaluate(clases);
    }

    @Test
    void unPuenteDeOtraCapacidadConsumeElPuertoYSuDtoSinViolaciones() {
        EvaluationResult resultado = evaluar(PuenteAutorizacionDeEjemplo.class);

        assertThat(resultado.hasViolation()).as(resultado.getFailureReport().toString()).isFalse();
    }

    @Test
    void elPuenteUsaElContratoReal() {
        AutorizacionPort doble = id -> new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA, Rol.VENDEDOR),
                Set.of(Permiso.USUARIO_VER, Permiso.CLIENTE_BUSCAR));
        PuenteAutorizacionDeEjemplo puente = new PuenteAutorizacionDeEjemplo(doble);

        assertThat(puente.claimsDe(UUID.randomUUID())).containsExactly(
                List.of("ADMIN_SISTEMA", "VENDEDOR"), List.of("cliente.buscar", "usuario.ver"));
        assertThat(puente.puedeVerUsuarios(UUID.randomUUID())).isTrue();
        assertThat(puente.exigeSegundoFactor(UUID.randomUUID())).isTrue();
    }

    @Test
    void elPuertoExportadoNoMencionaTiposInternos() {
        JavaClass puerto = new ClassFileImporter().importClasses(AutorizacionPort.class, PermisosResueltos.class)
                .get(AutorizacionPort.class);
        JavaClass dto = new ClassFileImporter().importClasses(PermisosResueltos.class)
                .get(PermisosResueltos.class);

        for (JavaClass exportado : List.of(puerto, dto)) {
            assertThat(exportado.getDirectDependenciesFromSelf())
                    .as(exportado.getSimpleName())
                    .allSatisfy(d -> assertThat(d.getTargetClass().getPackageName())
                            .doesNotContain(".domain.model").doesNotContain(".adapter."));
        }
    }

    @Test
    void unPuenteQueUsaElModeloInternoIncumpleLaRegla() {
        EvaluationResult resultado = evaluar(PuenteConModeloInterno.class);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().toString()).contains("PermisosEfectivos");
    }

    @Test
    void consumirAutorizacionSinPuenteIncumpleLaRegla() {
        EvaluationResult resultado = evaluar(ConsumoSinPuente.class);

        assertThat(resultado.hasViolation()).isTrue();
        assertThat(resultado.getFailureReport().toString()).contains("ConsumoSinPuente");
    }
}
