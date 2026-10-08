package br.com.anotaai.controller;

import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.UsuarioAtualService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {
    private final AuditoriaService auditoriaService;
    private final UsuarioAtualService usuarioAtualService;

    public AuditoriaController(AuditoriaService auditoriaService, UsuarioAtualService usuarioAtualService) {
        this.auditoriaService = auditoriaService;
        this.usuarioAtualService = usuarioAtualService;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return auditoriaService.listarDoUsuario(usuarioAtualService.obter().getId()).stream()
                .map(registro -> Map.<String, Object>of(
                        "id", registro.getId(),
                        "acao", registro.getAcao(),
                        "recurso", registro.getRecurso(),
                        "sucesso", registro.isSucesso(),
                        "criadoEm", registro.getCriadoEm()
                ))
                .toList();
    }
}
