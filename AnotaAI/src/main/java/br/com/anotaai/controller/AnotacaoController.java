package br.com.anotaai.controller;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.service.AnotacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/anotacoes")
public class AnotacaoController {
    private final AnotacaoService anotacaoService;

    public AnotacaoController(AnotacaoService anotacaoService) {
        this.anotacaoService = anotacaoService;
    }

    @GetMapping
    public List<Anotacao> listar(@RequestParam(required = false) String busca) {
        return anotacaoService.listar(busca);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Anotacao criar(@RequestBody Anotacao anotacao) {
        return anotacaoService.criar(anotacao);
    }

    @GetMapping("/{id}")
    public Anotacao buscar(@PathVariable Long id) {
        return anotacaoService.buscar(id);
    }

    @PutMapping("/{id}")
    public Anotacao atualizar(@PathVariable Long id, @RequestBody Anotacao anotacao) {
        return anotacaoService.atualizar(id, anotacao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        anotacaoService.excluir(id);
    }

    @GetMapping("/{id}/versoes")
    public List<Map<String, Object>> versoes(@PathVariable Long id) {
        return anotacaoService.listarVersoes(id).stream()
                .map(versao -> Map.<String, Object>of(
                        "id", versao.getId(),
                        "tipo", versao.getTipo(),
                        "conteudo", versao.getConteudo(),
                        "criadoEm", versao.getCriadoEm()
                ))
                .toList();
    }
}
