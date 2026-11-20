package br.com.anotaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenRouterLimiteTokensTest {
    ObjectMapper mapper = new ObjectMapper();
    RestClient.Builder builder = mock(RestClient.Builder.class, RETURNS_SELF);
    RestClient client = mock(RestClient.class);
    RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
    RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
    LimiteTokensIaService limite = mock(LimiteTokensIaService.class);
    LimiteTokensIaService.Reserva reserva = new LimiteTokensIaService.Reserva("7:periodo", 12000);
    OpenRouterService service;

    @BeforeEach
    void preparar() {
        when(builder.build()).thenReturn(client);
        when(client.post()).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(limite.maxTokensSaida()).thenReturn(4096);
        when(limite.reservar(anyLong())).thenReturn(reserva);
        service = new OpenRouterService(builder, mapper, "test-key", "test-model", limite);
    }

    JsonNode envelope(String conteudo) {
        return mapper.valueToTree(Map.of("choices", List.of(Map.of("message", Map.of("content", conteudo))),
                "usage", Map.of("total_tokens", 850)));
    }

    @Test
    void limitaSaidaEReservaAntesDeEnviarDepoisAjustaPeloConsumo() {
        when(response.body(JsonNode.class)).thenReturn(envelope("{\"resumo\":\"Síntese\"}"));
        service.gerar("Anotação", "resumo");
        var ordem = inOrder(limite, client, response);
        ordem.verify(limite).maxTokensSaida();
        ordem.verify(limite).reservar(longThat(estimativa -> estimativa > "Anotação".length()));
        ordem.verify(client).post();
        ordem.verify(response).body(JsonNode.class);
        ordem.verify(limite).concluir(reserva, 850);
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(request).body(captor.capture());
        assertThat(mapper.valueToTree(captor.getValue()).path("max_completion_tokens").asInt()).isEqualTo(4096);
    }

    @Test
    void bloqueiaResumoFlashcardsERevisaoSemContatarProvedor() {
        when(limite.reservar(anyLong())).thenThrow(new LimiteTokensIaException(Instant.now().plusSeconds(60)));
        assertThatThrownBy(() -> service.gerar("Anotação", "resumo")).isInstanceOf(LimiteTokensIaException.class);
        assertThatThrownBy(() -> service.gerar("Anotação", "flashcards", 5)).isInstanceOf(LimiteTokensIaException.class);
        assertThatThrownBy(() -> service.verificarInconsistencias("Aula", "Anotação"))
                .isInstanceOf(LimiteTokensIaException.class);
        verifyNoInteractions(client);
    }

    @Test
    void timeoutNaoDevolveReserva() {
        when(response.body(JsonNode.class)).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> service.gerar("Anotação", "resumo")).isInstanceOf(OpenRouterException.class);
        verify(limite).reservar(anyLong());
        verify(limite, never()).concluir(any(), anyLong());
    }

    @Test
    void respostaSemUsageConservaReserva() {
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("{\"resumo\":\"Síntese\"}");
        envelope.remove("usage");
        when(response.body(JsonNode.class)).thenReturn(envelope);
        service.gerar("Anotação", "resumo");
        verify(limite, never()).concluir(any(), anyLong());
    }

    @Test
    void cobraTokensMesmoQuandoResultadoGeradoEInvalido() {
        when(response.body(JsonNode.class)).thenReturn(envelope("conteúdo sem JSON"));
        assertThatThrownBy(() -> service.gerar("Anotação", "resumo")).isInstanceOf(OpenRouterException.class);
        verify(limite).concluir(reserva, 850);
    }

    @Test
    void revisaoComBuscaTambemDebitaOMesmoLimite() {
        when(response.body(JsonNode.class)).thenReturn(envelope("{\"sugestoes\":[],\"fontes\":[]}"));
        service.verificarInconsistencias("Aula", "Anotação");
        verify(limite).reservar(longThat(estimativa -> estimativa > 8192));
        verify(limite).concluir(reserva, 850);
    }
}
