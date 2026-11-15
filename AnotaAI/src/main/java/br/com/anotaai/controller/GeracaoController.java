package br.com.anotaai.controller;

import br.com.anotaai.service.OpenRouterService;
import br.com.anotaai.service.RegistroUsoIaService;
import br.com.anotaai.service.AnotacaoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anotacoes/gerar")
public class GeracaoController {
    private final OpenRouterService openRouter;
    private final RegistroUsoIaService usoIa;
    private final AnotacaoService anotacaoService;

    public GeracaoController(OpenRouterService openRouter, RegistroUsoIaService usoIa,
            AnotacaoService anotacaoService) {
        this.openRouter = openRouter;
        this.usoIa = usoIa;
        this.anotacaoService = anotacaoService;
    }

    public record Pedido(Long anotacaoId, Integer quantidade, String conteudo) {
    }

    @PostMapping("/{tipo}")
    public JsonNode gerar(@PathVariable String tipo, @RequestBody Pedido pedido) {
        if (pedido == null || pedido.anotacaoId() == null) {
            throw new IllegalArgumentException("Salve a anotação antes de solicitar a geração.");
        }
        var anotacao = anotacaoService.buscar(pedido.anotacaoId());
        if (pedido.conteudo() != null && !"resumo".equals(tipo)) {
            throw new IllegalArgumentException("O texto alternativo só pode ser usado para gerar resumo.");
        }
        var conteudoBase = pedido.conteudo() == null ? anotacao.getConteudo() : pedido.conteudo();
        var somenteTituloEConteudo = "Título: " + anotacao.getTitulo() + "\n\nConteúdo:\n" + conteudoBase;
        var resultado = openRouter.gerar(somenteTituloEConteudo, tipo, pedido.quantidade());
        usoIa.registrar(tipo);
        return resultado;
    }

    @PostMapping("/inconsistencias")
    public JsonNode verificarInconsistencias(@RequestBody Pedido pedido) {
        if (pedido == null || pedido.anotacaoId() == null) {
            throw new IllegalArgumentException("Salve a anotação antes de solicitar a verificação.");
        }
        var anotacao = anotacaoService.buscar(pedido.anotacaoId());
        var resultado = openRouter.verificarInconsistencias(anotacao.getTitulo(), anotacao.getConteudo());
        usoIa.registrar("inconsistencias");
        return resultado;
    }
}