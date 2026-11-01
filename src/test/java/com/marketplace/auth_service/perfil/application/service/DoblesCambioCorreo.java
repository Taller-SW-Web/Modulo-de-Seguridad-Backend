package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.port.out.CredencialesCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.CuentaCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.EfectosCambioCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.NotificacionesCorreoPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Dobles en memoria de los puertos de salida de FEAT-04-T3; {@code orden} registra el orden de las llamadas. */
public final class DoblesCambioCorreo {
    private DoblesCambioCorreo() {}

    public static final class Credenciales implements CredencialesCorreoPort {
        final List<String> orden;
        public boolean contrasenaCorrecta = true;
        public boolean revisionVigente = true;
        public String ultimaContrasena;
        public Credenciales(List<String> orden) { this.orden = orden; }
        @Override public Optional<RevisionContrasena> preverificar(UUID usuarioId, String contrasenaActual) {
            orden.add("preverificar");
            ultimaContrasena = contrasenaActual;
            return contrasenaCorrecta ? Optional.of(new RevisionContrasena() {}) : Optional.empty();
        }
        @Override public boolean confirmarRevision(RevisionContrasena revision) {
            orden.add("confirmarRevision");
            return revisionVigente;
        }
    }

    public static final class Cuentas implements CuentaCorreoPort {
        final List<String> orden;
        public final UUID id;
        public String correoActual = "actual@correo.com";
        public final Set<String> enUso = new HashSet<>();
        public boolean existe = true;
        public Cuentas(List<String> orden, UUID id) { this.orden = orden; this.id = id; }
        @Override public Optional<CuentaCorreo> leer(UUID usuarioId) {
            return existe && id.equals(usuarioId) ? Optional.of(new CuentaCorreo(id, correoActual)) : Optional.empty();
        }
        @Override public void bloquear(UUID usuarioId) { orden.add("bloquear"); }
        @Override public boolean correoEnUso(String correoNormalizado) { return enUso.contains(correoNormalizado); }
    }

    public static final class Notificaciones implements NotificacionesCorreoPort {
        final List<String> orden;
        public final List<String> confirmaciones = new ArrayList<>();
        public final List<String> avisos = new ArrayList<>();
        public Notificaciones(List<String> orden) { this.orden = orden; }
        @Override public void enviarConfirmacion(UUID usuarioId, String correoNuevo) {
            orden.add("confirmacion");
            confirmaciones.add(correoNuevo);
        }
        @Override public void avisarCorreoAnterior(String correoActual, String correoNuevo) {
            orden.add("aviso");
            avisos.add(correoActual + "->" + correoNuevo);
        }
    }

    public static final class Efectos implements EfectosCambioCorreoPort {
        final List<String> orden;
        public Efectos(List<String> orden) { this.orden = orden; }
        @Override public <T> T enTransaccion(Supplier<T> trabajo) {
            orden.add("tx");
            return trabajo.get();
        }
    }
}
