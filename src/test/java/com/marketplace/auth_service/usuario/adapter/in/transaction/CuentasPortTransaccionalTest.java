package com.marketplace.auth_service.usuario.adapter.in.transaction;

import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentaEscrituraAdapter;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioEstadoEfectivoPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentasPortTransaccionalTest {

    @Mock
    private ConsultaCuentasService consultaService;

    @Mock
    private CuentaEscrituraAdapter escrituraAdapter;

    @Mock
    private UsuarioRepositoryPort usuarioRepositoryPort;

    private CuentasPortTransaccional transaccional;

    private UUID usuarioId;
    private CuentaSnapshot snapshot;

    @BeforeEach
    void setUp() {
        transaccional = new CuentasPortTransaccional(consultaService, escrituraAdapter, usuarioRepositoryPort);
        usuarioId = UUID.randomUUID();
        snapshot = new CuentaSnapshot(
                usuarioId, "Juan", "Pérez", "juan@test.com", "+51999999999",
                EstadoCuenta.ACTIVO, EstadoCuenta.ACTIVO, true, false, 0,
                "DNI", "*****456", Set.of(CodigoRol.CLIENTE)
        );
    }

    @Test
    void buscarPorCorreoNormalizado_delegaAConsultaService_readOnly() {
        when(consultaService.buscarPorCorreoNormalizado("juan@test.com")).thenReturn(Optional.of(snapshot));

        Optional<CuentaSnapshot> result = transaccional.buscarPorCorreoNormalizado("juan@test.com");

        assertThat(result).isPresent().contains(snapshot);
        verify(consultaService).buscarPorCorreoNormalizado("juan@test.com");
    }

    @Test
    void leer_delegaAConsultaService_readOnly() {
        when(consultaService.leer(usuarioId)).thenReturn(Optional.of(snapshot));

        Optional<CuentaSnapshot> result = transaccional.leer(usuarioId);

        assertThat(result).isPresent().contains(snapshot);
        verify(consultaService).leer(usuarioId);
    }

    @Test
    void bloquear_usaLockByIdDeRepository_txRequerida() {
        when(usuarioRepositoryPort.lockById(usuarioId)).thenReturn(snapshot);

        CuentaSnapshot result = transaccional.bloquear(usuarioId);

        assertThat(result).isEqualTo(snapshot);
        verify(usuarioRepositoryPort).lockById(usuarioId);
    }

    @Test
    void contarActivos_delegaAConsultaService_readOnly() {
        when(consultaService.contarActivos(Set.of(usuarioId))).thenReturn(1L);

        long result = transaccional.contarActivos(Set.of(usuarioId));

        assertThat(result).isEqualTo(1L);
        verify(consultaService).contarActivos(Set.of(usuarioId));
    }

    @Test
    void consultar_delegaAConsultaService_readOnly() {
        FiltroCuenta filtro = new FiltroCuenta(null, null, null);
        Paginacion paginacion = new Paginacion(1, 20);
        PaginaResultado<CuentaSnapshot> pagina = new PaginaResultado<>(List.of(snapshot), 1, 20, 1L);

        when(consultaService.consultar(filtro, paginacion)).thenReturn(pagina);

        PaginaResultado<CuentaSnapshot> result = transaccional.consultar(filtro, paginacion);

        assertThat(result).isEqualTo(pagina);
        verify(consultaService).consultar(filtro, paginacion);
    }

    @Test
    void actualizarBasicos_delegaAEscrituraAdapter_mandatory() {
        when(escrituraAdapter.actualizarBasicos(eq(usuarioId), any(CambioBasicos.class))).thenReturn(snapshot);

        CuentaSnapshot result = transaccional.actualizarBasicos(usuarioId, new CambioBasicos("X", "Y", "Z", null, null));

        assertThat(result).isEqualTo(snapshot);
        verify(escrituraAdapter).actualizarBasicos(eq(usuarioId), any(CambioBasicos.class));
    }

    @Test
    void guardarContadores_delegaAEscrituraAdapter_mandatory() {
        ContadoresProteccion contadores = new ContadoresProteccion(2, 4);

        transaccional.guardarContadores(usuarioId, contadores);

        verify(escrituraAdapter).guardarContadores(eq(usuarioId), eq(contadores));
    }

    @Test
    void guardarMfaVoluntario_delegaAEscrituraAdapter_mandatory() {
        transaccional.guardarMfaVoluntario(usuarioId, true);

        verify(escrituraAdapter).guardarMfaVoluntario(eq(usuarioId), eq(true));
    }

    @Test
    void metodosTienenAnotacionesTransaccionalesCorrectas() throws NoSuchMethodException {
        // Lectura: REQUIRED + readOnly
        checkTx("buscarPorCorreoNormalizado", Propagation.REQUIRED, true, String.class);
        checkTx("leer", Propagation.REQUIRED, true, UUID.class);
        checkTx("contarActivos", Propagation.REQUIRED, true, Set.class);
        checkTx("consultar", Propagation.REQUIRED, true, FiltroCuenta.class, Paginacion.class);

        // Bloquear: REQUIRED + not readOnly
        checkTx("bloquear", Propagation.REQUIRED, false, UUID.class);

        // Escritura: MANDATORY + not readOnly
        checkTx("actualizarBasicos", Propagation.MANDATORY, false, UUID.class, CambioBasicos.class);
        checkTx("guardarContadores", Propagation.MANDATORY, false, UUID.class, ContadoresProteccion.class);
        checkTx("guardarMfaVoluntario", Propagation.MANDATORY, false, UUID.class, boolean.class);
    }

    private void checkTx(String methodName, Propagation expectedPropagation, boolean expectedReadOnly, Class<?>... paramTypes) throws NoSuchMethodException {
        Method method = CuentasPortTransaccional.class.getMethod(methodName, paramTypes);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).as("Method %s debe tener @Transactional", methodName).isNotNull();
        assertThat(tx.propagation()).as("Propagation de %s", methodName).isEqualTo(expectedPropagation);
        assertThat(tx.readOnly()).as("readOnly de %s", methodName).isEqualTo(expectedReadOnly);
    }
}