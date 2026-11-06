package br.com.anotaai.controller;

import br.com.anotaai.service.FlashcardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {
    private final FlashcardService service;
    public FlashcardController(FlashcardService service) { this.service = service; }
    @GetMapping
    public List<FlashcardService.Resumo> listar() { return service.listar(); }
    @GetMapping("/{id}")
    public FlashcardService.Detalhe buscar(@PathVariable Long id) { return service.buscar(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlashcardService.Detalhe salvar(@RequestBody FlashcardService.Pedido pedido) { return service.salvar(pedido); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) { service.excluir(id); }
}
