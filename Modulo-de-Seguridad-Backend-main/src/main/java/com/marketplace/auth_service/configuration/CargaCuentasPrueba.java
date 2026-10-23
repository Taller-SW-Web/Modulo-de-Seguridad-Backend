package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.RolesPruebaInterna;
import com.marketplace.auth_service.credencial.application.dto.InfraccionPassword;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialPruebaInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.CuentaPrueba;
import com.marketplace.auth_service.usuario.application.port.in.AltaCuentaPruebaInterna;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carga reproducible de las cuentas de prueba TEC-02 (EP-01-T007). Compone las
 * capacidades de sus dueños: identidad (Eva), credencial con el hasher y la política
 * reales (Juan José/Luis) y roles (Sergio). Ningún SQL sobre tablas ajenas.
 *
 * <p>Todo ocurre en una transacción: si una cuenta falla, no queda ninguna a medias.
 * Una cuenta cuyo UUID ya existe no se toca, así que repetir la carga no duplica
 * ni sobrescribe lo que se haya cambiado durante la demo. La contraseña llega por
 * configuración externa y nunca se registra.
 */
public final class CargaCuentasPrueba {

    /** Cuenta del catálogo TEC-02: etiqueta del plan, identidad y roles. */
    public record Cuenta(String etiqueta, CuentaPrueba identidad, Set<Rol> roles) { }

    /** Resultado sin datos sensibles: se puede registrar y compartir. */
    public record Resultado(String etiqueta, UUID id, String correo, Set<Rol> roles, boolean creada) { }

    private static final String DOMINIO = "@fixtures.g7.test";

    /**
     * A/B CLIENTE sin MFA voluntario; V VENDEDOR sin CLIENTE; ADM ADMIN_SISTEMA (MFA
     * efectivo por rol de gestión); M con MFA voluntario, L para bloqueo y C para
     * cambios de perfil o contraseña, separados para no alterar A/B en la demo.
     */
    public static final List<Cuenta> CATALOGO = List.of(
            cuenta("A", "00000000-0000-4000-8000-00000000f00a", "cliente.a", "Cliente A", false, Rol.CLIENTE),
            cuenta("B", "00000000-0000-4000-8000-00000000f00b", "cliente.b", "Cliente B", false, Rol.CLIENTE),
            cuenta("V", "00000000-0000-4000-8000-00000000f00c", "vendedor.v", "Vendedor V", false, Rol.VENDEDOR),
            cuenta("ADM", "00000000-0000-4000-8000-00000000f0ad", "admin", "Administrador", false,
                    Rol.ADMIN_SISTEMA),
            cuenta("M", "00000000-0000-4000-8000-00000000f00d", "cliente.mfa", "Cliente MFA", true, Rol.CLIENTE),
            cuenta("L", "00000000-0000-4000-8000-00000000f00e", "cliente.bloqueo", "Cliente Bloqueo", false,
                    Rol.CLIENTE),
            cuenta("C", "00000000-0000-4000-8000-00000000f00f", "cliente.cambios", "Cliente Cambios", false,
                    Rol.CLIENTE));

    private final AltaCuentaPruebaInterna cuentas;
    private final CredencialPruebaInterna credenciales;
    private final RolesPruebaInterna roles;
    private final CredencialesPort politica;
    private final TransactionTemplate transaccion;
    private final SecretoEfimero clave;
    private final List<Cuenta> catalogo;

    public CargaCuentasPrueba(AltaCuentaPruebaInterna cuentas, CredencialPruebaInterna credenciales,
                              RolesPruebaInterna roles, CredencialesPort politica,
                              PlatformTransactionManager transacciones, SecretoEfimero clave) {
        this(cuentas, credenciales, roles, politica, transacciones, clave, null);
    }

    /**
     * {@code correoAdm}: buzón real para la cuenta ADM, que necesita recibir el OTP en una demo
     * (MFA obligatorio por rol de gestión). Solo cambia el correo; el UUID sigue siendo el estable.
     */
    public CargaCuentasPrueba(AltaCuentaPruebaInterna cuentas, CredencialPruebaInterna credenciales,
                              RolesPruebaInterna roles, CredencialesPort politica,
                              PlatformTransactionManager transacciones, SecretoEfimero clave, String correoAdm) {
        this.catalogo = correoAdm == null || correoAdm.isBlank() ? CATALOGO : CATALOGO.stream()
                .map(c -> !"ADM".equals(c.etiqueta()) ? c : new Cuenta(c.etiqueta(),
                        new CuentaPrueba(c.identidad().id(), correoAdm, c.identidad().nombres(),
                                c.identidad().apellidos(), c.identidad().mfaVoluntario()), c.roles()))
                .toList();
        this.cuentas = Objects.requireNonNull(cuentas);
        this.credenciales = Objects.requireNonNull(credenciales);
        this.roles = Objects.requireNonNull(roles);
        this.politica = Objects.requireNonNull(politica);
        this.clave = Objects.requireNonNull(clave);
        this.transaccion = new TransactionTemplate(transacciones);
        this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public List<Resultado> cargar() {
        return transaccion.execute(estado -> {
            List<Resultado> resultados = new ArrayList<>();
            for (Cuenta cuenta : catalogo) {
                UUID id = cuenta.identidad().id();
                boolean creada = cuentas.asegurar(cuenta.identidad());
                if (creada) {
                    var validacion = politica.validarNueva(id, clave);
                    if (!validacion.valida()) {
                        String reglas = validacion.errores().stream().map(InfraccionPassword::regla)
                                .map(Enum::name).collect(Collectors.joining(", "));
                        throw new IllegalStateException("La clave de las cuentas de prueba no cumple la política ("
                                + reglas + "); no se creó ninguna cuenta");
                    }
                    credenciales.asegurar(id, clave);
                    roles.asegurar(id, cuenta.roles());
                }
                resultados.add(new Resultado(cuenta.etiqueta(), id, cuenta.identidad().correo(), cuenta.roles(),
                        creada));
            }
            return List.copyOf(resultados);
        });
    }

    private static Cuenta cuenta(String etiqueta, String id, String usuario, String nombres, boolean mfa, Rol rol) {
        return new Cuenta(etiqueta, new CuentaPrueba(UUID.fromString(id), usuario + DOMINIO, nombres, "Prueba G7", mfa),
                Set.of(rol));
    }
}
