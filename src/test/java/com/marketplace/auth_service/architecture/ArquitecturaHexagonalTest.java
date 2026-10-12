package com.marketplace.auth_service.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Fronteras del diseño BACKEND-HEXAGONAL 1.1.0; se ejecutan también en el CI. */
class ArquitecturaHexagonalTest {
    private static final String RAIZ = "com.marketplace.auth_service.";
    private static final Set<String> CAPACIDADES = Set.of("usuario", "sesion", "credencial", "token",
            "mfa", "proteccion", "autorizacion", "identidad", "auditoria", "mensajeria", "perfil");
    private static final JavaClasses PRODUCCION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.marketplace.auth_service");

    @Test
    void dominioYAplicacionSoloDependenDeJavaYDelNucleoPermitido() {
        classes().that().resideInAnyPackage(RAIZ + ".domain..", RAIZ + ".application..")
                .should(new ArchCondition<>("depender de Java, su propio núcleo y shared.domain") {
                    @Override
                    public void check(JavaClass origen, ConditionEvents eventos) {
                        String capacidad = capacidad(origen.getPackageName());
                        boolean dominio = origen.getPackageName().contains(".domain.")
                                || origen.getPackageName().endsWith(".domain");
                        for (var dependencia : origen.getDirectDependenciesFromSelf()) {
                            String destino = dependencia.getTargetClass().getPackageName();
                            boolean permitido = destino.startsWith("java.")
                                    || dentro(destino, RAIZ + "shared.domain")
                                    || (capacidad != null && dentro(destino, RAIZ + capacidad + ".domain"))
                                    || (!dominio && capacidad != null
                                        && dentro(destino, RAIZ + capacidad + ".application"));
                            if (!permitido) {
                                eventos.add(SimpleConditionEvent.violated(origen, dependencia.getDescription()));
                            }
                        }
                    }
                }).allowEmptyShould(true).check(PRODUCCION);
    }

    @Test
    void otraCapacidadSoloSeConsumeDesdeUnPuenteYPorExports() {
        classes().should(new ArchCondition<>("consumir capacidades ajenas por puentes a puertos y DTO exportados") {
            @Override
            public void check(JavaClass origen, ConditionEvents eventos) {
                String propietario = capacidad(origen.getPackageName());
                if (propietario == null) return;
                for (var dependencia : origen.getDirectDependenciesFromSelf()) {
                    String destino = dependencia.getTargetClass().getPackageName();
                    String proveedor = capacidad(destino);
                    if (proveedor == null || proveedor.equals(propietario)) continue;
                    boolean puente = dentro(origen.getPackageName(), RAIZ + propietario + ".adapter.out.capacidad");
                    boolean exportado = dentro(destino, RAIZ + proveedor + ".application.port.in")
                            || dentro(destino, RAIZ + proveedor + ".application.dto");
                    if (!puente || !exportado) {
                        eventos.add(SimpleConditionEvent.violated(origen, dependencia.getDescription()));
                    }
                }
            }
        }).check(PRODUCCION);
    }

    @Test
    void jpaYSpringDataQuedanEnPersistenciaOConfiguracion() {
        noClasses().that().resideOutsideOfPackages("..adapter.out.persistence..", RAIZ + "configuration..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .allowEmptyShould(true).check(PRODUCCION);
    }

    @Test
    void controladoresNoAccedenDirectamenteAPersistencia() {
        noClasses().that().resideInAPackage("..adapter.in.web..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.persistence..")
                .allowEmptyShould(true).check(PRODUCCION);
    }

    private static boolean dentro(String paquete, String prefijo) {
        return paquete.equals(prefijo) || paquete.startsWith(prefijo + ".");
    }

    private static String capacidad(String paquete) {
        if (!paquete.startsWith(RAIZ)) return null;
        String primerSegmento = paquete.substring(RAIZ.length()).split("\\.")[0];
        return CAPACIDADES.contains(primerSegmento) ? primerSegmento : null;
    }
}
