package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.mapper.UsuarioPersistenceMapper;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.MockitoSettings;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuentaEscrituraAdapterContractTest {

    @Mock
    private UsuarioJpaRepository usuarioJpaRepository;

    private CuentaEscrituraAdapter adapter;

    private UUID usuarioId;
    private UsuarioJpaEntity entity;

    @BeforeEach
    void setUp() {
        adapter = new CuentaEscrituraAdapter(usuarioJpaRepository);
        usuarioId = UUID.randomUUID();

        entity = new UsuarioJpaEntity();
        entity.setId(usuarioId);
        entity.setCorreo("juan@test.com");
        entity.setNombres("Juan");
        entity.setApellidos("Pérez");
        entity.setCelular("+51999999999");
        entity.setEstado(EstadoCuenta.ACTIVO);
        entity.setCorreoVerificado(true);
        entity.setCelularVerificado(false);
        entity.setMfaHabilitado(false);
        entity.setAceptaTerminos(true);
        entity.setCanalOrigen(com.marketplace.auth_service.usuario.domain.model.CanalOrigen.WEB);
        entity.setIntentosFallidosConsecutivos(0);
        entity.setBloqueosSeguidos(0);
        entity.setTipoDocumento("DNI");
        entity.setVersion(1L);

        lenient().when(usuarioJpaRepository.findById(eq(usuarioId))).thenReturn(java.util.Optional.of(entity));
        lenient().when(usuarioJpaRepository.saveAndFlush(any(com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void guardarMfaVoluntario_lanzaUnsupportedOperation() {
        assertThatThrownBy(() -> adapter.guardarMfaVoluntario(usuarioId, true))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SPEC-10");
    }

    @Test
    void metodosExigenTransaccionExistente_sinTx_lanzaExcepcion() throws NoSuchMethodException {
        assertThat(adapter.getClass().getMethod("actualizarBasicos", UUID.class, CambioBasicos.class)
                .getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);

        assertThat(adapter.getClass().getMethod("guardarContadores", UUID.class, ContadoresProteccion.class)
                .getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);

        assertThat(adapter.getClass().getMethod("guardarMfaVoluntario", UUID.class, boolean.class)
                .getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }
}