package com.marketplace.auth_service.identidad.adapter.in.transaction;

import com.marketplace.auth_service.identidad.application.dto.ResultadoTokenServicio;
import com.marketplace.auth_service.identidad.application.dto.SolicitudTokenServicio;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Frontera transaccional de {@code POST /auth/token}: una lectura del cliente y
 * sus scopes, de solo lectura y READ_COMMITTED. La firma no toca la base.
 */
public final class TokenServicioTransaccional implements TokenServicioPort {

    private final TokenServicioPort caso;
    private final TransactionTemplate tx;

    public TokenServicioTransaccional(TokenServicioPort caso, PlatformTransactionManager gestor) {
        this.caso = caso;
        this.tx = new TransactionTemplate(gestor);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public ResultadoTokenServicio emitir(SolicitudTokenServicio solicitud) {
        return tx.execute(estado -> caso.emitir(solicitud));
    }
}
