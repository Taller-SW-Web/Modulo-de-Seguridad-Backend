package com.marketplace.auth_service.credencial.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PasswordHistorial {
    public static final int MAX_ENTRADAS = 5;

    private static final Comparator<Entrada> ORDEN_ESTABLE =
            Comparator.comparing(Entrada::creadoEn).thenComparing(Entrada::id);

    private final List<Entrada> entradas;

    public PasswordHistorial(List<Entrada> entradas) {
        Objects.requireNonNull(entradas, "entradas");
        if (entradas.size() > MAX_ENTRADAS) {
            throw new IllegalArgumentException("El historial no puede exceder cinco entradas");
        }

        List<Entrada> ordenadas = new ArrayList<>(entradas);
        ordenadas.sort(ORDEN_ESTABLE);
        Set<UUID> ids = new HashSet<>();
        for (Entrada entrada : ordenadas) {
            if (!ids.add(entrada.id())) {
                throw new IllegalArgumentException("El historial contiene ids duplicados");
            }
        }
        this.entradas = List.copyOf(ordenadas);
    }

    public List<Entrada> entradas() {
        return entradas;
    }

    public PasswordHistorial agregar(UUID id, HashContrasena hash, Instant creadoEn) {
        List<Entrada> actualizadas = new ArrayList<>(entradas);
        actualizadas.add(new Entrada(id, hash, creadoEn));
        actualizadas.sort(ORDEN_ESTABLE);
        if (actualizadas.size() > MAX_ENTRADAS) {
            actualizadas.remove(0);
        }
        return new PasswordHistorial(actualizadas);
    }

    @Override
    public String toString() {
        return "PasswordHistorial[entradas=" + entradas.size() + "]";
    }

    public static final class Entrada {
        private final UUID id;
        private final HashContrasena hash;
        private final Instant creadoEn;

        public Entrada(UUID id, HashContrasena hash, Instant creadoEn) {
            this.id = Objects.requireNonNull(id, "id");
            this.hash = Objects.requireNonNull(hash, "hash");
            this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        }

        public UUID id() {
            return id;
        }

        public HashContrasena hash() {
            return hash;
        }

        public Instant creadoEn() {
            return creadoEn;
        }

        @Override
        public String toString() {
            return "PasswordHistorial.Entrada[REDACTADO]";
        }
    }
}
