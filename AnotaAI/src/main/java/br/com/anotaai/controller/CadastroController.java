package br.com.anotaai.controller;

import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.VerificacaoCadastroService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/cadastro")
public class CadastroController {
    private final VerificacaoCadastroService verificacaoService;
    private final AuditoriaService auditoriaService;

    public CadastroController(VerificacaoCadastroService verificacaoService, AuditoriaService auditoriaService) {
        this.verificacaoService = verificacaoService;
        this.auditoriaService = auditoriaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VerificacaoCadastroService.Desafio cadastrar(@RequestBody CadastroRequest dados) {
        return verificacaoService.iniciar(dados.nome(), dados.email(), dados.senha(), dados.dataNascimento(),
                dados.escolaridade(), dados.aceitouTermos(), dados.declarouMaioridade(), dados.versaoTermos());
    }

    @PostMapping("/confirmar")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> confirmar(@RequestBody ConfirmacaoRequest dados, HttpServletRequest request) {
        var usuario = verificacaoService.confirmar(dados.token());
        auditoriaService.registrar(usuario.getEmail(), "CADASTRO_USUARIO", "/api/auth/cadastro/confirmar",
                true, "Conta criada após verificação do e-mail.", request);
        return Map.of("id", usuario.getId(), "nome", usuario.getNome(), "email", usuario.getEmail(),
                "perfil", usuario.getPerfil(), "mensagem", "Conta criada com sucesso.");
    }

    @PostMapping("/reenviar")
    public Map<String, String> reenviar(@RequestBody ReenvioRequest dados) {
        verificacaoService.reenviar(dados.desafioId());
        return Map.of("mensagem", "Um novo e-mail de confirmação foi enviado.");
    }

    public record ConfirmacaoRequest(String token) { }
    public record ReenvioRequest(String desafioId) { }
    public record CadastroRequest(String nome, String email, String senha, LocalDate dataNascimento,
            String escolaridade, Boolean aceitouTermos, Boolean declarouMaioridade, String versaoTermos) { }
}
