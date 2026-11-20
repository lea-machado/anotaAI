package br.com.anotaai.controller;

import br.com.anotaai.model.DocumentoLegal;
import br.com.anotaai.service.TermosService;
import br.com.anotaai.service.UsuarioAtualService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/termos")
public class AdminTermosController {
    private final TermosService termos;
    private final UsuarioAtualService atual;

    public AdminTermosController(TermosService termos, UsuarioAtualService atual) {
        this.termos = termos;
        this.atual = atual;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoLegal publicar(@RequestBody PublicacaoRequest dados) {
        var usuario = atual.obter();
        if (!"ADMIN".equals(usuario.getPerfil())) {
            throw new org.springframework.security.access.AccessDeniedException("Acesso restrito ao administrador.");
        }
        return termos.publicar(dados.conteudo(), usuario.getEmail());
    }

    public record PublicacaoRequest(String conteudo) {
    }
}
