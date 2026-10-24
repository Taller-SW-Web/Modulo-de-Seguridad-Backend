package com.marketplace.auth_service.direccion;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import com.marketplace.auth_service.direccion.application.port.out.CuentaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import com.marketplace.auth_service.direccion.application.port.out.EventosDireccionesPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Dobles en memoria de los puertos de salida de direcciones, con registro de llamadas y de su orden. */
public final class DoblesDirecciones {
    private DoblesDirecciones() {
    }

    public static final class Persistencia implements DireccionesPersistidasPort {
        public final List<String> orden;
        public final List<UUID> duenos = new ArrayList<>();
        public final List<DireccionDatos> filas = new ArrayList<>();
        public int llamadas;

        public Persistencia(List<String> orden) {
            this.orden = orden;
        }

        @Override
        public List<DireccionDatos> listar(UUID usuarioId) {
            llamadas++;
            orden.add("listar");
            List<DireccionDatos> propias = new ArrayList<>();
            for (int i = 0; i < filas.size(); i++) {
                if (duenos.get(i).equals(usuarioId)) propias.add(filas.get(i));
            }
            propias.sort(Comparator.comparing((DireccionDatos d) -> !d.esPredeterminada()));
            return propias;
        }

        @Override
        public DireccionDatos crear(UUID usuarioId, DatosNuevaDireccion datos) {
            llamadas++;
            orden.add("crear");
            boolean primera = !duenos.contains(usuarioId);
            boolean predeterminada = primera || Boolean.TRUE.equals(datos.esPredeterminada());
            if (predeterminada) {
                for (int i = 0; i < filas.size(); i++) {
                    if (duenos.get(i).equals(usuarioId) && filas.get(i).esPredeterminada()) {
                        DireccionDatos d = filas.get(i);
                        filas.set(i, new DireccionDatos(d.id(), d.etiqueta(), d.departamento(), d.provincia(),
                                d.distrito(), d.direccion(), d.referencia(), false));
                    }
                }
            }
            DireccionDatos nueva = new DireccionDatos(UUID.randomUUID(), datos.etiqueta(), datos.departamento(),
                    datos.provincia(), datos.distrito(), datos.direccion(), datos.referencia(), predeterminada);
            duenos.add(usuarioId);
            filas.add(nueva);
            return nueva;
        }
    }

    public static final class Cuentas implements CuentaDireccionesPort {
        public final List<String> orden;
        public final Set<UUID> existentes = new HashSet<>();
        public int consultas;
        public int bloqueos;

        public Cuentas(List<String> orden) {
            this.orden = orden;
        }

        @Override
        public boolean existe(UUID usuarioId) {
            consultas++;
            return existentes.contains(usuarioId);
        }

        @Override
        public void bloquear(UUID usuarioId) {
            bloqueos++;
            orden.add("bloquear");
        }
    }

    public static final class Eventos implements EventosDireccionesPort {
        public final List<UUID> publicados = new ArrayList<>();

        @Override
        public void direccionesActualizadas(UUID usuarioId) {
            publicados.add(usuarioId);
        }
    }
}
