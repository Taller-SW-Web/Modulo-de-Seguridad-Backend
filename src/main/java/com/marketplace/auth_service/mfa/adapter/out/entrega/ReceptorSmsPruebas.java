package com.marketplace.auth_service.mfa.adapter.out.entrega;

import java.util.ArrayDeque;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Receptor solo para el perfil explícito pruebas-sms. Sin logs ni endpoint público. */
@Component
@Profile("pruebas-sms")
public final class ReceptorSmsPruebas {
    public record Entrega(String destino, String codigo) {
        @Override public String toString() { return "Entrega[REDACTADO]"; }
    }
    private final ArrayDeque<Entrega> entregas = new ArrayDeque<>();
    public synchronized void recibir(String destino, String codigo) {
        if (entregas.size() == 50) entregas.removeFirst();
        entregas.addLast(new Entrega(destino, codigo));
    }
    public synchronized List<Entrega> retirar() {
        var copia = List.copyOf(entregas); entregas.clear(); return copia;
    }
}
