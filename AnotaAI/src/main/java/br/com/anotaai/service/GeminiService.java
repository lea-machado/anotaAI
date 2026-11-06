package br.com.anotaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GeminiService(RestClient.Builder builder, ObjectMapper mapper,
                         @Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model:gemini-3.5-flash-lite}") String model) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = builder.baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public JsonNode gerar(String conteudo, String tipo) {
        if (conteudo == null || conteudo.isBlank() || conteudo.length() > 30000) {
            throw new IllegalArgumentException("Informe uma anotação com até 30.000 caracteres para gerar material de estudo.");
        }
        if (!"resumo".equals(tipo) && !"flashcards".equals(tipo)) {
            throw new IllegalArgumentException("Escolha resumo ou flashcards.");
        }
        if (apiKey.isBlank()) {
            throw new GeminiException("O Gemini ainda não foi configurado. Defina GEMINI_API_KEY no servidor.");
        }
        var texto = Map.of("type", "STRING");
        Map<String, Object> schema = "resumo".equals(tipo)
                ? Map.of("type", "OBJECT", "properties", Map.of("resumo", texto), "required", List.of("resumo"))
                : Map.of("type", "OBJECT", "properties", Map.of("flashcards", Map.of(
                        "type", "ARRAY", "minItems", 1, "maxItems", 10,
                        "items", Map.of("type", "OBJECT", "properties", Map.of("pergunta", texto, "resposta", texto),
                                "required", List.of("pergunta", "resposta")))), "required", List.of("flashcards"));
        var instrucao = PromptsAnotaAi.instrucao(tipo);
        var body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instrucao))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", conteudo)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema));
        try {
            JsonNode response = client.post().uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            return extrair(response, tipo);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new GeminiException("O limite de uso do Gemini foi atingido. Tente novamente mais tarde.");
            }
            if (exception.getStatusCode().is5xxServerError()) {
                throw new GeminiException("O Gemini está temporariamente indisponível ou com alta demanda. Tente novamente em alguns instantes.");
            }
            if (exception.getStatusCode().value() == 404) {
                throw new GeminiException("O modelo do Gemini não está disponível. Atualize GEMINI_MODEL no servidor para um modelo disponível na sua conta.");
            }
            throw new GeminiException("Não foi possível gerar o material. Verifique a chave e o modelo do Gemini no servidor.");
        } catch (RestClientException exception) {
            throw new GeminiException("O Gemini não respondeu a tempo ou está indisponível. Tente novamente.");
        }
    }

    JsonNode extrair(JsonNode response, String tipo) {
        try {
            JsonNode candidate = response.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) throw new IllegalStateException();
            var text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean()) text.append(part.path("text").asText(""));
            }
            JsonNode result = mapper.readTree(text.toString());
            if ("resumo".equals(tipo)) {
                if (!textoValido(result.path("resumo"))) throw new IllegalStateException();
            } else {
                JsonNode cards = result.path("flashcards");
                if (!cards.isArray() || cards.isEmpty() || cards.size() > 10) throw new IllegalStateException();
                for (JsonNode card : cards) {
                    if (!textoValido(card.path("pergunta")) || !textoValido(card.path("resposta"))) {
                        throw new IllegalStateException();
                    }
                }
            }
            return result;
        } catch (Exception exception) {
            throw new GeminiException("O Gemini não retornou um resultado válido. Tente novamente com uma anotação mais detalhada.");
        }
    }

    private boolean textoValido(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank();
    }
}
