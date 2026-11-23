package br.com.anotaai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class UsuarioIdPublicoTest {
    @Test
    void novasContasRecebemUuidsDistintosSemDependerDaSequencia() {
        var primeira = new Usuario();
        var segunda = new Usuario();
        assertThat(UUID.fromString(primeira.getIdPublico()).version()).isEqualTo(4);
        assertThat(primeira.getIdPublico()).isNotEqualTo(segunda.getIdPublico());
        assertThat(primeira.getId()).isNull();
    }

    @Test
    void serializacaoOcultaChaveInterna() {
        var usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 7L);
        var json = new ObjectMapper().valueToTree(usuario);
        assertThat(json.has("id")).isFalse();
        assertThat(json.path("idPublico").asText()).isEqualTo(usuario.getIdPublico());
    }

    @Test
    void persistenciaPreservaIdPublicoEChaveInterna() throws Exception {
        var context = new MongoMappingContext();
        var conversions = new org.springframework.data.mongodb.core.convert.MongoCustomConversions(List.of());
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        var usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 7L);
        var documento = new Document();
        converter.write(usuario, documento);
        assertThat(documento).containsEntry("_id", 7L).containsEntry("idPublico", usuario.getIdPublico());
        var lido = converter.read(Usuario.class, documento);
        assertThat(lido.getId()).isEqualTo(7L);
        assertThat(lido.getIdPublico()).isEqualTo(usuario.getIdPublico());
    }
}
