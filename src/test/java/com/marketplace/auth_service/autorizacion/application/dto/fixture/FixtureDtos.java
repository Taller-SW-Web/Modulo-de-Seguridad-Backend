package com.marketplace.auth_service.autorizacion.application.dto.fixture;

import com.marketplace.auth_service.autorizacion.domain.fixture.ModeloInterno;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FixtureDtos {
    private FixtureDtos() {}
    public record Valido(UUID usuarioId, List<String> roles) {}
    public record DominioAnidado(Optional<List<ModeloInterno>> resultado) {}
    public static final class CampoPrivado {
        private ModeloInterno estado;
    }
}
