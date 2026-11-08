package br.com.anotaai.controller;

import br.com.anotaai.service.FlashcardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {
    private final FlashcardService service;
    public FlashcardController(FlashcardService service) { this.service = service; }
    @GetMapping
    public List<FlashcardService.Resumo> listar(@RequestParam(required = false) Long anotacaoId) { return service.listar(anotacaoId); }
    @GetMapping("/{id}")
    public FlashcardService.Detalhe buscar(@PathVariable Long id) { return service.buscar(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardService.Detalhe salvar(@RequestBody FlashcardService.Pedido pedido) { return service.salvar(pedido); }
    @PostMapping("/{id}/cartoes/{indice}/resultado")
    public FlashcardService.Detalhe registrarResultado(@PathVariable Long id, @PathVariable int indice,
                                                        @RequestBody ResultadoRequest request) {
        if (request == null || request.correto() == null) throw new IllegalArgumentException("Informe se a resposta estava correta.");
        return service.registrarResultado(id, indice, request.correto());
    }
    @GetMapping("/revisao-erros")
    public List<FlashcardService.CartaoComErro> revisarErros(@RequestParam(required = false) Long anotacaoId) {
        return service.revisarErros(anotacaoId);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) { service.excluir(id); }
    public record ResultadoRequest(Boolean correto) {}
}
