package com.marketplace.auth_service.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.*;
import java.util.Set;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/** Reglas ejecutables compartidas por producción y pruebas de regresión. */
final class ContratosExportados {
    private static final String RAIZ = "com.marketplace.auth_service.";
    private static final Set<String> CAPACIDADES = Set.of("usuario", "sesion", "credencial", "token",
            "mfa", "proteccion", "autorizacion", "identidad", "auditoria", "mensajeria", "perfil", "direccion");

    private ContratosExportados() {}

    static ArchRule puertosDeEntrada() { return regla("application.port.in"); }
    static ArchRule dtoDeAplicacion() { return regla("application.dto"); }

    private static ArchRule regla(String superficie) {
        return classes().that(new DescribedPredicate<>("pertenecer a " + superficie + " de una capacidad") {
            @Override public boolean test(JavaClass clase) {
                String capacidad = capacidad(clase.getPackageName());
                return capacidad != null && dentro(clase.getPackageName(), RAIZ + capacidad + "." + superficie);
            }
        }).should(new ArchCondition<>("exportar solo Java, shared.domain y puertos/DTO propios") {
            @Override public void check(JavaClass origen, ConditionEvents eventos) {
                String propietario = capacidad(origen.getPackageName());
                // Incluye campos, parámetros, retornos, genéricos y tipos heredados.
                // No necesita ninguna clase consumidora que invoque este contrato.
                for (var dependencia : origen.getDirectDependenciesFromSelf()) {
                    JavaClass destino = dependencia.getTargetClass();
                    if (destino.isArray()) destino = destino.getBaseComponentType();
                    String paquete = destino.getPackageName();
                    boolean permitido = destino.isPrimitive()
                            || paquete.startsWith("java.")
                            || dentro(paquete, RAIZ + "shared.domain")
                            || dentro(paquete, RAIZ + propietario + ".application.dto")
                            || dentro(paquete, RAIZ + propietario + ".application.port.in");
                    if (!permitido) {
                        eventos.add(SimpleConditionEvent.violated(origen, dependencia.getDescription()
                                + ". Tipo interno/no exportable: " + destino.getName()
                                + "; usar DTO de application.dto, sin exponer dominio o infraestructura."));
                    }
                }
            }
        }).allowEmptyShould(true);
    }

    private static boolean dentro(String paquete, String prefijo) {
        return paquete.equals(prefijo) || paquete.startsWith(prefijo + ".");
    }
    private static String capacidad(String paquete) {
        if (!paquete.startsWith(RAIZ)) return null;
        String primero = paquete.substring(RAIZ.length()).split("\\.")[0];
        return CAPACIDADES.contains(primero) ? primero : null;
    }
}
