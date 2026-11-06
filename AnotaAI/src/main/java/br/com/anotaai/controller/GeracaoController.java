package br.com.anotaai.controller;

import br.com.anotaai.service.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anotacoes/gerar")
public class GeracaoController {
    private final GeminiService gemini;

    public GeracaoController(GeminiService gemini) {
        this.gemini = gemini;
    }

    public record Pedido(String conteudo) {}

    @PostMapping("/{tipo}")
    public JsonNode gerar(@PathVariable String tipo, @RequestBody Pedido pedido) {
        return gemini.gerar(pedido.conteudo(), tipo);
    }
}
