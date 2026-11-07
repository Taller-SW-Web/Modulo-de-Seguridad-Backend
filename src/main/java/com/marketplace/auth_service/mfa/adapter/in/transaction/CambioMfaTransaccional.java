package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Un rechazo de familia puede marcar rollback desde SesionesPort; no intentar confirmarlo. */
public final class CambioMfaTransaccional implements CambioMfaPort {
    private final CambioMfaPort caso;
    private final TransactionTemplate tx;
    public CambioMfaTransaccional(CambioMfaPort caso, PlatformTransactionManager gestor) {
        this.caso = caso;
        tx = new TransactionTemplate(gestor);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    private ResultadoCambioMfa ejecutar(Supplier<ResultadoCambioMfa> trabajo) {
        return tx.execute(estado -> {
            var resultado = trabajo.get();
            if (resultado instanceof ResultadoCambioMfa.Error e && "TOKEN_INVALIDO".equals(e.code())) estado.setRollbackOnly();
            return resultado;
        });
    }
    @Override public ResultadoCambioMfa solicitar(ActorUsuario a, String accion, ContextoPeticion c) {
        return ejecutar(() -> caso.solicitar(a, accion, c));
    }
    @Override public ResultadoCambioMfa reenviar(ActorUsuario a, String token, ContextoPeticion c) {
        return ejecutar(() -> caso.reenviar(a, token, c));
    }
    @Override public ResultadoCambioMfa confirmar(ActorUsuario a, String token, String codigo, ContextoPeticion c) {
        return ejecutar(() -> caso.confirmar(a, token, codigo, c));
    }
}
