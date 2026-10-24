package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.identidad.application.dto.ResultadoConsultaIdentidad;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;
import com.marketplace.auth_service.usuario.application.port.out.ConsultaIdentidadServicioPort;
import com.marketplace.auth_service.usuario.domain.exception.ConsultaInvalidaException;
import com.marketplace.auth_service.usuario.domain.exception.CuentaNoEncontradaException;

/**
 * Puente a {@code ConsultaIdentidadPort} (Sergio, SPEC-18): {@code GET /usuarios/{id}}
 * es una sola ruta compartida por persona y servicio, así que el caso borde de
 * identificador inválido o cuenta inexistente se traduce a las excepciones
 * propias de esta capacidad, ya traducidas por {@link
 * com.marketplace.auth_service.usuario.adapter.in.web.UsuariosErroresAdvice}.
 */
public final class ConsultaIdentidadServicioAdapter implements ConsultaIdentidadServicioPort {

    private final ConsultaIdentidadPort identidad;

    public ConsultaIdentidadServicioAdapter(ConsultaIdentidadPort identidad) {
        this.identidad = identidad;
    }

    @Override
    public UsuarioServicioVista obtener(ActorServicio actor, String idTexto) {
        return switch (identidad.obtener(actor, idTexto)) {
            case ResultadoConsultaIdentidad.Encontrado encontrado -> {
                var v = encontrado.usuario();
                yield new UsuarioServicioVista(v.id(), v.nombres(), v.apellidos(), v.nombreCompleto(), v.correo(),
                        v.celular(), v.estado(), v.correoVerificado(), v.celularVerificado(), v.roles(),
                        v.tipoDocumento(), v.documento(), v.documentoEnmascarado());
            }
            case ResultadoConsultaIdentidad.Invalida invalida ->
                    throw new ConsultaInvalidaException(invalida.campo(), invalida.mensaje());
            case ResultadoConsultaIdentidad.NoEncontrado noEncontrado ->
                    throw new CuentaNoEncontradaException(noEncontrado.id());
        };
    }
}
