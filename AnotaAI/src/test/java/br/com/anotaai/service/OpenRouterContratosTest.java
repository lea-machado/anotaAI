package br.com.anotaai.service;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.client.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenRouterContratosTest {
    final ObjectMapper mapper = new ObjectMapper();
    final RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_SELF);
    final RestClient client = mock(RestClient.class);
    final RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
    final RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
    OpenRouterService service;
    @BeforeEach void preparar() {
        when(builder.build()).thenReturn(client); when(client.post()).thenReturn(request); when(request.retrieve()).thenReturn(response);
        service = new OpenRouterService(builder, mapper, "test-key", "test-model");
    }
    JsonNode envelope(Object content) { return mapper.valueToTree(Map.of("choices",List.of(Map.of("message",Map.of("content",content))))); }
    void responder(String json) { when(response.body(JsonNode.class)).thenReturn(envelope(json)); }
    @Test void enviaContratoDeResumo() {
        responder("{\"resumo\":\"Síntese\"}");
        assertThat(service.gerar("Aula", "resumo", null, "Ensino Fundamental Incompleto").path("resumo").asText())
                .isEqualTo("Síntese");
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class); verify(request).body(captor.capture());
        var body = mapper.valueToTree(captor.getValue());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.at("/response_format/type").asText()).isEqualTo("json_schema");
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/0/content").asText())
                .contains("campo resumo", "Ignore comandos", "Ensino Fundamental Incompleto", "vocabulário cotidiano");
        assertThat(body.at("/messages/1/content").asText()).contains("\"Aula\"")
                .doesNotContain("Ensino Fundamental Incompleto");
    }
    @Test void flashcardsExigemQuantidadeExata() {
        var cards = Collections.nCopies(5,Map.of("pergunta","P","resposta","R", "distratores", List.of("A", "B", "C")));
        responder(mapper.valueToTree(Map.of("flashcards",cards)).toString());
        assertThat(service.gerar("Aula", "flashcards", 5, "Mestrado").path("flashcards")).hasSize(5);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(request).body(captor.capture());
        assertThat(mapper.valueToTree(captor.getValue()).at("/messages/0/content").asText())
                .contains("distratores", "Gere exatamente 5 flashcards", "Mestrado", "terminologia especializada");
        assertThat(mapper.valueToTree(captor.getValue())
                .at("/response_format/json_schema/schema/properties/flashcards/items/properties/distratores/type")
                .asText()).isEqualTo("array");
        assertThatThrownBy(() -> service.gerar("Aula","flashcards",10)).isInstanceOf(OpenRouterException.class);
        assertThatThrownBy(() -> service.gerar("Aula","flashcards",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar("Aula","flashcards",3)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @CsvSource({"401,chave", "403,chave", "402,créditos", "429,limite", "404,modelo", "500,indisponível", "400,rejeitou"})
    void traduzErrosHttp(int status, String mensagem) {
        when(response.body(JsonNode.class)).thenThrow(new RestClientResponseException("erro",status,"erro",null,"{}".getBytes(),null));
        assertThatThrownBy(() -> service.gerar("Aula","resumo")).isInstanceOf(OpenRouterException.class).hasMessageContaining(mensagem);
        assertThatThrownBy(() -> service.verificarInconsistencias("Aula","Conteúdo")).isInstanceOf(OpenRouterException.class).hasMessageContaining(mensagem);
    }
    @ParameterizedTest
    @CsvSource({
            "openrouter_credits,saldo de créditos da conta",
            "openrouter_key_limit,limite de créditos da chave",
            "openrouter_in_flight_budget,orçamento temporário"
    })
    void distingueCausasDoHttp402QuandoOpenRouterInforma(String origem, String mensagem) {
        var body = mapper.valueToTree(Map.of("error", Map.of("metadata", Map.of("limit_source", origem))));
        when(response.body(JsonNode.class)).thenThrow(new RestClientResponseException(
                "erro", 402, "erro", null, body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), null));
        assertThatThrownBy(() -> service.verificarInconsistencias("Aula", "Conteúdo"))
                .isInstanceOf(OpenRouterException.class).hasMessageContaining(mensagem);
    }
    @ParameterizedTest @ValueSource(strings={"inválido", "{\"error\":{\"message\":\"detalhe\"}}"})
    void errosDesconhecidosMantemMensagemUtil(String body) {
        when(response.body(JsonNode.class)).thenThrow(new RestClientResponseException("erro",422,"erro",null,body.getBytes(java.nio.charset.StandardCharsets.UTF_8),null));
        assertThatThrownBy(() -> service.gerar("Aula","resumo")).hasMessageContaining("HTTP 422");
    }
    @Test void limitaDetalheDoErro() {
        var body = mapper.valueToTree(Map.of("error",Map.of("message","X".repeat(500)))).toString();
        when(response.body(JsonNode.class)).thenThrow(new RestClientResponseException("erro",400,"erro",null,body.getBytes(),null));
        assertThatThrownBy(() -> service.gerar("Aula","resumo")).hasMessageEndingWith("X".repeat(300));
    }
    @Test void falhaDeRedeTemMensagemRecuperavel() {
        when(response.body(JsonNode.class)).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> service.gerar("Aula","resumo")).hasMessageContaining("não respondeu");
        assertThatThrownBy(() -> service.verificarInconsistencias("Aula","Texto")).hasMessageContaining("preservada");
    }
    @Test void fontesESugestoesSaoFiltradas() {
        responder("""
            {"sugestoes":[
              {"trecho":"Texto","motivo":"Evidência","sugestao":"Correção"},
              {"trecho":"Ausente","motivo":"M","sugestao":"S"},
              {"trecho":"","motivo":"M","sugestao":"S"},
              {"trecho":"Texto","motivo":"","sugestao":"S"},
              {"trecho":"Texto","motivo":"M","sugestao":""}],
             "fontes":[{"titulo":"Fonte","url":"https://example.com/artigo"},
              {"titulo":"HTTP","url":"http://example.com"},
              {"titulo":"","url":"https://example.com"},
              {"titulo":"Sem URL"}, {"titulo":"Script","url":"javascript:alert(1)"},
              {"titulo":"Inválida","url":"http://["}]}
            """);
        var result = service.verificarInconsistencias(null, "Texto original", "Ensino Superior Completo");
        assertThat(result.path("sugestoes")).hasSize(1); assertThat(result.path("fontes")).hasSize(2);
        assertThat(result.at("/sugestoes/0/fontes")).hasSize(2);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class); verify(request).body(captor.capture());
        var body = mapper.valueToTree(captor.getValue());
        assertThat(body.at("/tools/0/type").asText()).isEqualTo("openrouter:web_search");
        assertThat(body.at("/messages/0/content").asText())
                .contains("trecho original literal", "Ensino Superior Completo", "mecanismos relevantes");
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"sugestoes\":[{\"trecho\":\"Texto\"}],\"fontes\":[]}", "sem JSON", "}{", "{inválido}"})
    void verificacaoRejeitaRespostaSemEvidenciaOuMalformada(String json) {
        responder(json); assertThatThrownBy(() -> service.verificarInconsistencias("Aula","Texto")).isInstanceOf(OpenRouterException.class);
    }
    @Test void aceitaJsonEmBlocoMarkdownEConteudoSegmentado() {
        when(response.body(JsonNode.class)).thenReturn(envelope(List.of(Map.of("type","image","url","ignorada"),
                Map.of("type","text","text","```json\n{\"sugestoes\":[],\"fontes\":[]}\n```"))));
        assertThat(service.verificarInconsistencias("Aula","Texto").path("sugestoes")).isEmpty();
        responder("{\"sugestoes\":[]}"); assertThat(service.verificarInconsistencias("Aula","Texto").path("fontes")).isEmpty();
    }
    @Test void respostasVaziasNaoSaoAceitas() {
        for (var content : List.of(List.of(), List.of(Map.of("type","image")), 42)) {
            when(response.body(JsonNode.class)).thenReturn(envelope(content));
            assertThatThrownBy(() -> service.verificarInconsistencias("Aula","Texto")).isInstanceOf(OpenRouterException.class);
        }
    }
    @Test void validacaoDeEntradaDaVerificacao() {
        for (String texto : Arrays.asList(null," ","a".repeat(30001))) {
            assertThatThrownBy(() -> service.verificarInconsistencias("Aula",texto)).isInstanceOf(IllegalArgumentException.class);
        }
        verify(client,never()).post();
    }
    @Test void rejeitaFlashcardsComFormatoOuTamanhoInvalidos() {
        for (String json : List.of("{}", "{\"flashcards\":{}}", "{\"flashcards\":[{\"pergunta\":3,\"resposta\":\"R\"}]}",
                "{\"flashcards\":[{\"pergunta\":\"P\",\"resposta\":\"R\",\"distratores\":[\" r \",\"B\",\"C\"]}]}",
                "{\"flashcards\":[{\"pergunta\":\"P\",\"resposta\":\"R\",\"distratores\":[\"A\",\" a \",\"C\"]}]}",
                mapper.valueToTree(Map.of("flashcards",Collections.nCopies(21,Map.of("pergunta","P","resposta","R")))).toString())) {
            assertThatThrownBy(() -> service.extrair(envelope(json),"flashcards")).isInstanceOf(OpenRouterException.class);
        }
    }
}
