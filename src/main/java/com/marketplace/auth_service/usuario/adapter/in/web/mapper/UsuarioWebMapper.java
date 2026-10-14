package com.marketplace.auth_service.usuario.adapter.in.web.mapper;

import com.marketplace.auth_service.usuario.adapter.in.web.dto.BloqueoResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.PaginaUsuariosResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioResponse;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UsuarioWebMapper {

    private UsuarioWebMapper() {}

    public static UsuarioResponse toResponse(CuentaSnapshot snapshot) {
        if (snapshot == null) return null;

        BloqueoResponse bloqueo = null;
        if (snapshot.estadoEfectivo() == EstadoCuenta.BLOQUEADO) {
            bloqueo = new BloqueoResponse("AUTOMATICO", null, null);
        }

        return new UsuarioResponse(
                snapshot.id(),
                snapshot.nombres(),
                snapshot.apellidos(),
                snapshot.nombreCompleto(),
                snapshot.correo(),
                snapshot.celular(),
                snapshot.estadoEfectivo().name(),
                snapshot.correoVerificado(),
                false,
                snapshot.mfaHabilitado(),
                null,
                null,
                null,
                null,
                snapshot.roles() != null ? snapshot.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()) : Set.of(),
                snapshot.tipoDocumento(),
                null,
                snapshot.documentoEnmascarado(),
                bloqueo
        );
    }

    public static PaginaUsuariosResponse toPageResponse(PaginaResultado<CuentaSnapshot> pagina) {
        if (pagina == null) return null;
        List<UsuarioResponse> usuarios = pagina.elementos().stream()
                .map(UsuarioWebMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
        return new PaginaUsuariosResponse(usuarios, pagina.pagina(), pagina.tamano(), pagina.total());
    }
}