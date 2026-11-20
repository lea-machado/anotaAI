package br.com.anotaai.controller;

import br.com.anotaai.model.DocumentoLegal;
import br.com.anotaai.service.TermosService;
import br.com.anotaai.service.UsuarioAtualService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/termos")
public class TermosController {
    private final TermosService termos;
    private final UsuarioAtualService atual;

    public TermosController(TermosService termos, UsuarioAtualService atual) {
        this.termos = termos;
        this.atual = atual;
    }

    @GetMapping("/atuais")
    public DocumentoLegal atuais() {
        return termos.atual();
    }

    @GetMapping("/versoes")
    public List<DocumentoLegal> versoes() {
        return termos.historico();
    }

    @GetMapping("/versoes/{id}")
    public DocumentoLegal versao(@PathVariable String id) {
        return termos.versao(id);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return termos.status(atual.obter());
    }

    @PostMapping("/aceitar")
    public Map<String, Object> aceitar(@RequestBody AceiteRequest dados) {
        return termos.aceitar(atual.obter(), dados.aceitou(), dados.versao());
    }

    public record AceiteRequest(Boolean aceitou, String versao) {
    }
}
