package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Las migraciones que aplicará Flyway en la ventana (db/migration) son exactamente las revisadas
 * y ensayadas en docs/migraciones-propuestas, que usan las pruebas de PostgreSQL de cada dueño.
 */
class MigracionesAlineadasTest {

    private static final Path PROPUESTAS = Path.of("docs/migraciones-propuestas");
    private static final Path FLYWAY = Path.of("src/main/resources/db/migration");

    @Test
    void cadaVersionDeFlywayEsIdenticaALaPropuestaRevisada() throws Exception {
        List<Path> propuestas;
        try (Stream<Path> s = Files.list(PROPUESTAS)) {
            propuestas = s.filter(p -> p.getFileName().toString().matches("V1\\d\\d__.+\\.sql")).sorted().toList();
        }
        List<String> enFlyway;
        try (Stream<Path> s = Files.list(FLYWAY)) {
            enFlyway = s.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertThat(propuestas).hasSize(8);
        assertThat(enFlyway).containsExactlyElementsOf(propuestas.stream().map(p -> p.getFileName().toString()).toList());
        for (Path propuesta : propuestas) {
            assertThat(Files.readString(FLYWAY.resolve(propuesta.getFileName())))
                    .as(propuesta.getFileName().toString())
                    .isEqualTo(Files.readString(propuesta));
        }
    }
}
