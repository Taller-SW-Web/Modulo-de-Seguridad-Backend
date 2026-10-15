package com.marketplace.auth_service.perfil.adapter.out.persistence.mapper;

import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilClienteJpaEntity;
import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilVendedorJpaEntity;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;
import com.marketplace.auth_service.perfil.domain.model.PerfilCliente;
import com.marketplace.auth_service.perfil.domain.model.PerfilVendedor;

/** Entidad -> dominio. No descifra ni interpreta el documento: lo entrega como dato protegido. */
public final class PerfilPersistenceMapper {

    private PerfilPersistenceMapper() {
    }

    public static PerfilCliente aDominio(PerfilClienteJpaEntity e) {
        return new PerfilCliente(e.getUsuarioId(), e.getFechaNacimiento(), documentoDe(e));
    }

    public static PerfilVendedor aDominio(PerfilVendedorJpaEntity e) {
        return new PerfilVendedor(e.getUsuarioId(), e.getCodigoVendedor(), e.getTienda(), e.getFechaIngreso());
    }

    /**
     * Los cuatro datos del documento van todos o ninguno (ck_perfil_cliente_documento).
     * Si la fila los trae a medias no se finge ausencia: el constructor de DocumentoProtegido falla.
     */
    private static DocumentoProtegido documentoDe(PerfilClienteJpaEntity e) {
        boolean sinDocumento = e.getTipoDocumento() == null
                && e.getDocumentoCifrado() == null
                && e.getDocumentoKeyId() == null
                && e.getDocumentoHmac() == null;
        if (sinDocumento) {
            return null;
        }
        return new DocumentoProtegido(
                e.getTipoDocumento(), e.getDocumentoCifrado(), e.getDocumentoKeyId(), e.getDocumentoHmac());
    }
}
