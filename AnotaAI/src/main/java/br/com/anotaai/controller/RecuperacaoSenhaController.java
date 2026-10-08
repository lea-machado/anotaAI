package br.com.anotaai.controller;

import br.com.anotaai.service.RecuperacaoSenhaService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/recuperacao")
public class RecuperacaoSenhaController {
    private final RecuperacaoSenhaService service;
    public RecuperacaoSenhaController(RecuperacaoSenhaService service) { this.service = service; }

    @PostMapping("/solicitar")
    public Map<String, Object> solicitar(@RequestBody SolicitarRequest request) {
        var desafio = service.solicitar(request.email());
        return Map.of("desafioId", desafio, "mensagem",
                "Se o e-mail estiver cadastrado, você receberá um código válido por 10 minutos. Aguarde um minuto entre solicitações.");
    }

    @PostMapping("/redefinir")
    public Map<String, String> redefinir(@RequestBody RedefinirRequest request) {
        service.redefinir(request.desafioId(), request.codigo(), request.novaSenha());
        return Map.of("mensagem", "Senha alterada com sucesso. Entre com sua nova senha.");
    }

    public record SolicitarRequest(String email) {}
    public record RedefinirRequest(UUID desafioId, String codigo, String novaSenha) {}
}
