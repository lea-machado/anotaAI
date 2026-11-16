package br.com.anotaai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiService service = new GeminiService(RestClient.builder(), mapper, "", "gemini-3.5-flash-lite");

    @Test
    void rejeitaEntradaInvalidaAntesDeChamarGemini() {
        assertThrows(IllegalArgumentException.class, () -> service.gerar(null, "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar(" ", "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar("a".repeat(30001), "resumo"));
        assertThrows(IllegalArgumentException.class, () -> service.gerar("conteúdo", "outro"));
        assertThrows(GeminiException.class, () -> service.gerar("conteúdo", "resumo"));
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
        var configured = new GeminiService(builder, mapper, "chave-ficticia", "gemini-3.5-flash-lite");
        when(response.body(com.fasterxml.jackson.databind.JsonNode.class))
                .thenThrow(new org.springframework.web.client.RestClientResponseException(
                        "Indisponível", 503, "Service Unavailable", null, null, null))
                .thenThrow(new org.springframework.web.client.RestClientResponseException(
                        "Modelo indisponível", 404, "Not Found", null, null, null));
        assertTrue(assertThrows(GeminiException.class, () -> configured.gerar("Anotação", "resumo"))
                .getMessage().contains("temporariamente indisponível"));
        assertTrue(assertThrows(GeminiException.class, () -> configured.gerar("Anotação", "flashcards"))
                .getMessage().contains("OPENROUTER_MODEL"));
    }

    @Test
    void extraiResumoEFlashcards() {
        assertEquals("Resumo da aula", service.extrair(envelope("{\"resumo\":\"Resumo da aula\"}", "STOP"), "resumo")
                .path("resumo").asText());
        var cards = service.extrair(
                envelope("{\"flashcards\":[{\"pergunta\":\"O quê?\",\"resposta\":\"Conceito\"}]}", "STOP"),
                "flashcards");
        assertEquals("Conceito", cards.path("flashcards").path(0).path("resposta").asText());
    }

    @Test
    void rejeitaRespostaBloqueadaTruncadaOuInvalida() {
        assertThrows(GeminiException.class, () -> service.extrair(mapper.createObjectNode(), "resumo"));
        assertThrows(GeminiException.class, () -> service.extrair(envelope("{}", "MAX_TOKENS"), "resumo"));
        assertThrows(GeminiException.class, () -> service.extrair(envelope("não é JSON", "STOP"), "resumo"));
        assertThrows(GeminiException.class, () -> service.extrair(envelope("{\"resumo\":\" \"}", "STOP"), "resumo"));
        assertThrows(GeminiException.class,
                () -> service.extrair(envelope("{\"flashcards\":[]}", "STOP"), "flashcards"));
        assertThrows(GeminiException.class, () -> service
                .extrair(envelope("{\"flashcards\":[{\"pergunta\":\"Pergunta\"}]}", "STOP"), "flashcards"));
    }

    private com.fasterxml.jackson.databind.JsonNode envelope(String text, String reason) {
        return mapper.valueToTree(Map.of(
                "choices", List.of(
                        Map.of(
                                "finish_reason", reason,
                                "message", Map.of(
                                        "role", "assistant",
                                        "content", text)))));
    }

}
