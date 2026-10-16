package com.marketplace.auth_service.autorizacion.application.port.in.fixture;

import com.marketplace.auth_service.autorizacion.domain.fixture.ModeloInterno;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.fixture.EntidadInterna;
import com.marketplace.auth_service.autorizacion.application.dto.fixture.FixtureDtos;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Casos intencionalmente válidos/inválidos, solo en src/test. */
public final class FixturePuertos {
    private FixturePuertos() {}
    public interface Directo { ModeloInterno resolver(UUID usuarioId); }
    public interface Generico { Optional<List<ModeloInterno>> resolver(UUID usuarioId); }
    public interface Parametro { void guardar(ModeloInterno valor); }
    public interface Arreglo { ModeloInterno[] resolver(); }
    public interface Base<T> { T resolver(); }
    public interface Heredado extends Base<ModeloInterno> {}
    public interface Persistencia { EntidadInterna resolver(); }
    public interface Spring { Page<FixtureDtos.Valido> resolver(); }
    public interface Valido { Optional<List<FixtureDtos.Valido>> resolver(UUID usuarioId); }
    public interface DtoConDominio { FixtureDtos.DominioAnidado resolver(); }
}
