package br.com.anotaai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompatibilidadeDocumentosTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void flashcardPersistidoPreservaProgressoEEstadoDeRevisao() throws Exception {
        var card = mapper.readValue(
                "{\"pergunta\":\"P\",\"resposta\":\"R\",\"acertos\":2,\"erros\":1,\"pendenteRevisao\":true}",
                Flashcard.class);
        assertThat(card.isPendenteRevisao()).isTrue();
        card.registrarResultado(true);
        var json = mapper.valueToTree(card);
        assertThat(json.path("acertos").asInt()).isEqualTo(3);
        assertThat(json.path("erros").asInt()).isEqualTo(1);
        assertThat(json.path("pendenteRevisao").asBoolean()).isFalse();
    }

    @Test
    void identificadoresPersistidosSaoPreservados() throws Exception {
        assertThat(mapper.readValue("{\"id\":7}", CodigoAutenticacao.class).getId()).isEqualTo(7L);
        assertThat(mapper.readValue("{\"id\":8}", CodigoRecuperacao.class).getId()).isEqualTo(8L);
        assertThat(mapper.readValue("{\"id\":9}", RegistroUsoIa.class).getId()).isEqualTo(9L);
    }

    @Test
    void auditoriaLegadaPodeSerLidaParaMigracao() throws Exception {
        var json = "{\"email\":\"legado@example.com\",\"detalhes\":\"legado\",\"enderecoIp\":\"127.0.0.1\",\"userAgent\":\"teste\"}";
        var registro = mapper.readValue(json, RegistroAuditoria.class);
        assertThat(mapper.valueToTree(registro).path("email").asText()).isEqualTo("legado@example.com");
        assertThat(registro.getDetalhes()).isEqualTo("legado");
        assertThat(registro.getEnderecoIp()).isEqualTo("127.0.0.1");
        assertThat(registro.getUserAgent()).isEqualTo("teste");
    }
}