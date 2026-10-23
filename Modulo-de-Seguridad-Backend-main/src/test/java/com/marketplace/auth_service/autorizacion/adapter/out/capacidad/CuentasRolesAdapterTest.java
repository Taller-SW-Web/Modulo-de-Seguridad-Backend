package com.marketplace.auth_service.autorizacion.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Puente de roles a {@code CuentasPort}: orden de locks, ausentes y estado efectivo. */
class CuentasRolesAdapterTest {

    private final Map<UUID, CuentaSnapshot> cuentas = new HashMap<>();
    private final List<UUID> bloqueadas = new ArrayList<>();
    private final List<Set<UUID>> conteos = new ArrayList<>();

    private final CuentasPort doble = new CuentasPort() {
        @Override
        public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CuentaSnapshot> leer(UUID id) {
            return Optional.ofNullable(cuentas.get(id));
        }

        @Override
        public CuentaSnapshot bloquear(UUID id) {
            bloqueadas.add(id);
            CuentaSnapshot cuenta = cuentas.get(id);
            if (cuenta == null) {
                throw new IllegalArgumentException("Cuenta inexistente");
            }
            return cuenta;
        }

        @Override
        public long contarActivos(Set<UUID> ids) {
            conteos.add(ids);
            return ids.stream().filter(id -> cuentas.containsKey(id)
                    && "ACTIVO".equals(cuentas.get(id).estadoEfectivo())).count();
        }

        @Override
        public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
            throw new UnsupportedOperationException();
        }
    };

    private final CuentasRolesAdapter puente = new CuentasRolesAdapter(doble);

    private UUID cuenta(String estadoBase, String estadoEfectivo) {
        UUID id = UUID.randomUUID();
        cuentas.put(id, new CuentaSnapshot(id, "Ana", "Pérez", "ana@g7.test", null, estadoBase, estadoEfectivo,
                true, false, false, 0, 0));
        return id;
    }

    @Test
    void bloqueaExactamenteEnElOrdenRecibido() {
        UUID a = cuenta("ACTIVO", "ACTIVO");
        UUID b = cuenta("ACTIVO", "ACTIVO");

        puente.bloquear(List.of(b, a));

        assertThat(bloqueadas).containsExactly(b, a);
    }

    @Test
    void unaCuentaInexistenteNoSeBloqueaYQuedaFueraDelResultado() {
        UUID existe = cuenta("ACTIVO", "ACTIVO");
        UUID noExiste = UUID.randomUUID();

        Map<UUID, CuentaRoles> resultado = puente.bloquear(List.of(noExiste, existe));

        assertThat(resultado).containsOnlyKeys(existe);
        assertThat(bloqueadas).containsExactly(existe);
    }

    @Test
    void entregaElEstadoEfectivoYNoElDeLaTabla() {
        UUID bloqueada = cuenta("ACTIVO", "BLOQUEADO");

        CuentaRoles cuenta = puente.bloquear(List.of(bloqueada)).get(bloqueada);

        assertThat(cuenta.estado()).isEqualTo("BLOQUEADO");
        assertThat(cuenta.activa()).isFalse();
        assertThat(cuenta.nombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(cuenta.correo()).isEqualTo("ana@g7.test");
    }

    @Test
    void cuentaActivosConElEstadoEfectivoDeCuentas() {
        UUID activa = cuenta("ACTIVO", "ACTIVO");
        UUID bloqueada = cuenta("ACTIVO", "BLOQUEADO");

        assertThat(puente.contarActivos(Set.of(activa, bloqueada))).isEqualTo(1);
        assertThat(puente.contarActivos(Set.of())).isZero();
        assertThat(conteos).hasSize(1);
    }
}
