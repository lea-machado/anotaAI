package br.com.anotaai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeminiService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public GeminiService(RestClient.Builder builder, ObjectMapper mapper,
                         @Value("${openrouter.api-key:}") String apiKey,
                         @Value("${openrouter.model:openai/gpt-4o-mini-2024-07-18}") String model) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder.baseUrl("https://openrouter.ai/api/v1")
                .requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public JsonNode gerar(String conteudo, String tipo) {
        return gerar(conteudo, tipo, "flashcards".equals(tipo) ? 10 : null);
    }

    public JsonNode gerar(String conteudo, String tipo, Integer quantidade) {
        if (conteudo == null || conteudo.isBlank() || conteudo.length() > 30000) {
            throw new IllegalArgumentException("Informe uma anotação com até 30.000 caracteres para gerar material de estudo.");
        }
        if (!"resumo".equals(tipo) && !"flashcards".equals(tipo)) {
            throw new IllegalArgumentException("Escolha resumo ou flashcards.");
        }
        if ("flashcards".equals(tipo) && (quantidade == null || !Set.of(5, 10, 15, 20).contains(quantidade))) {
            throw new IllegalArgumentException("Escolha 5, 10, 15 ou 20 flashcards.");
        }
        validarConfiguracao();

        Map<String, Object> schema = esquema(tipo);
        var instrucao = PromptsAnotaAi.instrucao(tipo)
                + ("flashcards".equals(tipo) ? "\nGere exatamente " + quantidade + " flashcards." : "")
                + "\nResponda apenas com um objeto JSON válido conforme o formato solicitado.";
        try {
            JsonNode response = completar(instrucao, conteudo, schema, false);
            return extrair(response, tipo, quantidade);
        } catch (GeminiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw erroOpenRouter(exception);
        } catch (RestClientException exception) {
            throw new GeminiException("O OpenRouter não respondeu a tempo ou está indisponível. Tente novamente.");
        }
    }

    public JsonNode verificarInconsistencias(String titulo, String conteudo) {
        if (conteudo == null || conteudo.isBlank() || conteudo.length() > 30000) {
            throw new IllegalArgumentException("Informe uma anotação com até 30.000 caracteres para verificar.");
        }
        validarConfiguracao();
        var instrucao = """
                Analise a anotação acadêmica e aponte somente afirmações factuais claramente incorretas ou imprecisas, não opiniões.
                Use a ferramenta de busca na web e fontes confiáveis e diretamente relevantes. Não invente fatos, fontes ou URLs.
                Responda apenas JSON no formato definido: sugestoes contém trecho, motivo e sugestao; fontes contém titulo e url.
                A sugestão deve substituir diretamente um trecho literal e só deve ser apresentada se fontes consultadas sustentarem a correção.
                Se não encontrar erro factual confirmado, retorne listas vazias em sugestoes e fontes.
                Não altere a anotação. A decisão pertence ao estudante.
                """;
        var entrada = "Título: " + (titulo == null ? "" : titulo) + "\n\nConteúdo:\n" + conteudo;
        try {
            JsonNode response = completar(instrucao, entrada, esquemaInconsistencias(), true);
            JsonNode result = lerObjetoJson(conteudoResposta(response));
            JsonNode suggestions = result.path("sugestoes");
            if (!suggestions.isArray()) throw new GeminiException("O OpenRouter retornou um formato inválido para a verificação.");
            JsonNode sources = result.path("fontes");
            var validSources = mapper.createArrayNode();
            if (sources.isArray()) {
                for (JsonNode source : sources) {
                    if (!textoValido(source.path("titulo")) || !textoValido(source.path("url"))) continue;
                    try {
                        String scheme = URI.create(source.path("url").asText()).getScheme();
                        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) continue;
                        var validSource = mapper.createObjectNode();
                        validSource.set("titulo", source.path("titulo"));
                        validSource.set("url", source.path("url"));
                        validSources.add(validSource);
                    } catch (IllegalArgumentException ignored) {
                        // Descarta URLs inválidas retornadas pelo modelo.
                    }
                }
            }
            if (!suggestions.isEmpty() && validSources.isEmpty()) {
                throw new GeminiException("A busca não retornou fontes válidas para sustentar uma correção. O texto original foi preservado.");
            }
            var normalized = mapper.createObjectNode();
            var outputSuggestions = mapper.createArrayNode();
            for (JsonNode suggestion : suggestions) {
                if (!textoValido(suggestion.path("trecho")) || !textoValido(suggestion.path("motivo"))
                        || !textoValido(suggestion.path("sugestao"))) continue;
                var item = mapper.createObjectNode();
                item.set("trecho", suggestion.path("trecho"));
                item.set("motivo", suggestion.path("motivo"));
                item.set("sugestao", suggestion.path("sugestao"));
                item.set("fontes", validSources.deepCopy());
                outputSuggestions.add(item);
            }
            normalized.set("sugestoes", outputSuggestions);
            normalized.set("fontes", validSources);
            return normalized;
        } catch (GeminiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw erroOpenRouter(exception);
        } catch (RestClientException exception) {
            throw new GeminiException("A busca do OpenRouter está indisponível. A anotação foi preservada.");
        } catch (Exception exception) {
            throw new GeminiException("A verificação não retornou fontes ou sugestões válidas. A anotação foi preservada.");
        }
    }

    private JsonNode completar(String system, String user, Map<String, Object> schema, boolean busca) {
        var messages = List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user));
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages);
        if (busca) {
            body.put("tools", List.of(Map.of("type", "openrouter:web_search")));
            body.put("response_format", Map.of("type", "json_object"));
        } else {
            var jsonSchema = new LinkedHashMap<String, Object>();
            jsonSchema.put("name", "material_estudo");
            jsonSchema.put("strict", true);
            jsonSchema.put("schema", schema);
            body.put("response_format", Map.of("type", "json_schema", "json_schema", jsonSchema));
            body.put("provider", Map.of("require_parameters", true));
        }
        return client.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://anotaai.local")
                .header("X-Title", "AnotaAI")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class);
    }

    private Map<String, Object> esquema(String tipo) {
        var texto = Map.<String, Object>of("type", "string");
        if ("resumo".equals(tipo)) {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of("resumo", texto), "required", List.of("resumo"));
        }
        var card = Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("pergunta", texto, "resposta", texto),
                "required", List.of("pergunta", "resposta"));
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("flashcards", Map.of("type", "array", "items", card)),
                "required", List.of("flashcards"));
    }

    private Map<String, Object> esquemaInconsistencias() {
        var texto = Map.<String, Object>of("type", "string");
        var suggestion = Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("trecho", texto, "motivo", texto, "sugestao", texto),
                "required", List.of("trecho", "motivo", "sugestao"));
        var source = Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("titulo", texto, "url", texto), "required", List.of("titulo", "url"));
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("sugestoes", Map.of("type", "array", "items", suggestion),
                        "fontes", Map.of("type", "array", "items", source)),
                "required", List.of("sugestoes", "fontes"));
    }

    JsonNode extrair(JsonNode response, String tipo) {
        return extrair(response, tipo, null);
    }

    JsonNode extrair(JsonNode response, String tipo, Integer quantidade) {
        try {
            JsonNode result = mapper.readTree(conteudoResposta(response));
            if ("resumo".equals(tipo)) {
                if (!textoValido(result.path("resumo"))) throw new IllegalStateException();
            } else {
                JsonNode cards = result.path("flashcards");
                if (!cards.isArray() || cards.isEmpty() || cards.size() > 20
                        || (quantidade != null && cards.size() != quantidade)) throw new IllegalStateException();
                for (JsonNode card : cards) {
                    if (!textoValido(card.path("pergunta")) || !textoValido(card.path("resposta"))) {
                        throw new IllegalStateException();
                    }
                }
            }
            return result;
        } catch (Exception exception) {
            throw new GeminiException("O OpenRouter não retornou um resultado válido. Tente novamente com uma anotação mais detalhada.");
        }
    }

    private String conteudoResposta(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            var text = new StringBuilder();
            for (JsonNode part : content) if (part.path("type").asText().equals("text")) text.append(part.path("text").asText());
            if (!text.isEmpty()) return text.toString();
        }
        throw new GeminiException("O OpenRouter retornou uma resposta vazia.");
    }

    private JsonNode lerObjetoJson(String content) throws Exception {
        String texto = content.strip();
        if (texto.startsWith("```")) {
            texto = texto.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").strip();
        }
        int inicio = texto.indexOf('{');
        int fim = texto.lastIndexOf('}');
        if (inicio < 0 || fim < inicio) throw new IllegalArgumentException("O modelo não retornou um objeto JSON.");
        return mapper.readTree(texto.substring(inicio, fim + 1));
    }

    private void validarConfiguracao() {
        if (apiKey.isBlank()) throw new GeminiException("O OpenRouter ainda não foi configurado. Defina OPENROUTER_API_KEY no servidor.");
    }

    private GeminiException erroOpenRouter(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) return new GeminiException("A chave do OpenRouter foi recusada. Confira OPENROUTER_API_KEY.");
        if (status == 402) return new GeminiException("O saldo ou limite de créditos do OpenRouter foi atingido.");
        if (status == 429) return new GeminiException("O limite de consultas do OpenRouter foi atingido. Tente novamente mais tarde.");
        if (status == 404) return new GeminiException("O modelo não foi encontrado ou não está disponível no OpenRouter. Confira OPENROUTER_MODEL.");
        if (status >= 500) return new GeminiException("O OpenRouter ou o provedor do modelo está temporariamente indisponível.");
        String detail = "";
        try {
            detail = mapper.readTree(exception.getResponseBodyAsString()).path("error").path("message").asText("");
        } catch (Exception ignored) {
            // A resposta do gateway pode não conter JSON.
        }
        if (detail.length() > 300) detail = detail.substring(0, 300);
        return new GeminiException("O OpenRouter rejeitou a solicitação (HTTP " + status + ")"
                + (detail.isBlank() ? ". Confira o modelo e os parâmetros enviados." : ": " + detail));
    }

    private boolean textoValido(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank();
    }
}
