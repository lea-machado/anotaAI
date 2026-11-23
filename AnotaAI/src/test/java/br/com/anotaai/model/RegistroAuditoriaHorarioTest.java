package br.com.anotaai.model;

import br.com.anotaai.service.AuditoriaService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RegistroAuditoriaHorarioTest {
    @Test
    void timestampMongoLegadoEConservadoAoLerESalvarComoInstant() throws Exception {
        var conversions = new MongoCustomConversions(List.of());
        var context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var instante = Instant.parse("2026-11-23T01:30:00Z");
        var legado = new Document("_id", 7L).append("criadoEm", Date.from(instante));
        var registro = converter.read(RegistroAuditoria.class, legado);
        assertThat(registro.getCriadoEm()).isEqualTo(instante);
        var gravado = new Document();
        converter.write(registro, gravado);
        assertThat(gravado.getDate("criadoEm")).isEqualTo(legado.getDate("criadoEm"));
    }

    @Test
    void filtroDoDiaIncluiMadrugadaUtcPertencenteANoiteDeBrasilia() {
        var dia = LocalDate.of(2026, 11, 22);
        var periodo = AuditoriaService.periodoBrasilia(dia, dia);
        assertThat(periodo.contains(Instant.parse("2026-11-22T02:59:59Z"))).isFalse();
        assertThat(periodo.contains(Instant.parse("2026-11-22T03:00:00Z"))).isTrue();
        assertThat(periodo.contains(Instant.parse("2026-11-23T02:59:59Z"))).isTrue();
        assertThat(periodo.contains(Instant.parse("2026-11-23T03:00:00Z"))).isFalse();
    }
}
