package br.com.anotaai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenRouterServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenRouterService service = new OpenRouterService(RestClient.builder(), mapper, "", "openai/gpt-4o-mini-2024-07-18");

    @Test
    void rejeitaEntradaInvalidaAntesDeChamarOpenRouter() {
        assertThrows(IllegalArgumentException.class, () -> service.gerar(null, "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar(" ", "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar("a".repeat(30001), "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar("conteúdo", "outro"));
        assertThrows(OpenRouterException.class, () -> service.gerar("conteúdo", "resumo"));
    }

    @Test
    void diferenciaModeloIndisponivelDeFalhaTemporaria() {
        var builder = mock(RestClient.Builder.class, RETURNS_SELF);
        var client = mock(RestClient.class);
        var request = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
        var response = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(builder.build()).thenReturn(client);
        var configured = new OpenRouterService(builder, mapper, "chave-ficticia", "openai/gpt-4o-mini-2024-07-18");
        when(response.body(com.fasterxml.jackson.databind.JsonNode.class))
                .thenThrow(new org.springframework.web.client.RestClientResponseException(
                        "Indisponível", 503, "Service Unavailable", null, null, null))
                .thenThrow(new org.springframework.web.client.RestClientResponseException(
                        "Modelo indisponível", 404, "Not Found", null, null, null));
        assertTrue(assertThrows(OpenRouterException.class, () -> configured.gerar("Anotação", "resumo"))
                .getMessage().contains("temporariamente indisponível"));
        assertTrue(assertThrows(OpenRouterException.class, () -> configured.gerar("Anotação", "flashcards"))
                .getMessage().contains("OPENROUTER_MODEL"));
    }

    @Test
    void extraiResumoEFlashcards() {
        assertEquals("Resumo da aula", service.extrair(envelope("{\"resumo\":\"Resumo da aula\"}", "STOP"), "resumo")
                .path("resumo").asText());
        var cards = service.extrair(envelope("""
                {"flashcards":[{"pergunta":"O quê?","resposta":"Conceito","distratores":["A","B","C"]}]}
                """, "STOP"), "flashcards");
        assertEquals("Conceito", cards.path("flashcards").path(0).path("resposta").asText());
        assertEquals(3, cards.at("/flashcards/0/distratores").size());
    }

    @Test
    void rejeitaRespostaBloqueadaTruncadaOuInvalida() {
        assertThrows(OpenRouterException.class, () -> service.extrair(mapper.createObjectNode(), "resumo"));
        assertThrows(OpenRouterException.class, () -> service.extrair(envelope("{}", "MAX_TOKENS"), "resumo"));
        assertThrows(OpenRouterException.class, () -> service.extrair(envelope("não é JSON", "STOP"), "resumo"));
        assertThrows(OpenRouterException.class, () -> service.extrair(envelope("{\"resumo\":\" \"}", "STOP"), "resumo"));
        assertThrows(OpenRouterException.class, () -> service.extrair(envelope("{\"flashcards\":[]}", "STOP"), "flashcards"));
        assertThrows(OpenRouterException.class, () -> service.extrair(envelope("{\"flashcards\":[{\"pergunta\":\"Pergunta\"}]}", "STOP"), "flashcards"));
    }

    private com.fasterxml.jackson.databind.JsonNode envelope(String text, String reason) {
        return mapper.valueToTree(Map.of("choices", List.of(Map.of("finish_reason", reason,
                "message", Map.of("content", text)))));
    }
}
