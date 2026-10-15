package com.marketplace.auth_service.sesion;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EP-02-T010: reiniciar conserva claves y familias porque nada de sesión vive
 * en estáticos del proceso. Las claves se cargan del entorno/archivo en cada
 * arranque (ver `IdentidadConfigTest`, kid estable) y las familias en
 * PostgreSQL. Este test falla si aparece un caché estático mutable en
 * `sesion` (los `Logger` y constantes `final` están permitidos).
 */
class SinEstadoEstaticoTest {

    @Test
    void sesionNoGuardaEstadoEnEstaticos() {
        JavaClasses produccion = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.marketplace.auth_service.sesion");
        List<String> mutables = new ArrayList<>();
        for (var clase : produccion) {
            for (JavaField campo : clase.getFields()) {
                if (campo.getName().contains("$")
                        || !campo.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)) {
                    continue;
                }
                boolean esFinal = campo.getModifiers()
                        .contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL);
                String tipo = campo.getRawType().getName();
                boolean contenedor = tipo.startsWith("java.util.") || tipo.startsWith("[");
                if (!esFinal || contenedor) {
                    mutables.add(clase.getName() + "#" + campo.getName());
                }
            }
        }
        assertThat(mutables).isEmpty();
    }
}
