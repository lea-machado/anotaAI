package br.com.anotaai.controller;

import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/cadastro")
public class CadastroController {
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public CadastroController(UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> cadastrar(@RequestBody CadastroRequest dados, HttpServletRequest request) {
        var emailInformado = dados.email() == null ? "" : dados.email().trim().toLowerCase();
        try {
            var usuario = usuarioService.criarUsuarioComum(
                    dados.nome(), dados.email(), dados.senha(), dados.dataNascimento(), dados.escolaridade(),
                    dados.aceitouTermos(), dados.declarouMaioridade(), dados.versaoTermos());
            auditoriaService.registrar(usuario.getEmail(), "CADASTRO_USUARIO", "/api/auth/cadastro",
                    true, "Conta de usuario criada.", request);
            return Map.of(
                    "id", usuario.getId(),
                    "nome", usuario.getNome(),
                    "email", usuario.getEmail(),
                    "perfil", usuario.getPerfil(),
                    "mensagem", "Conta criada com sucesso."
            );
        } catch (IllegalArgumentException exception) {
            auditoriaService.registrar(emailInformado, "CADASTRO_USUARIO", "/api/auth/cadastro",
                    false, exception.getMessage(), request);
            throw exception;
        }
    }

    public record CadastroRequest(
            String nome,
            String email,
            String senha,
            LocalDate dataNascimento,
            String escolaridade,
            Boolean aceitouTermos,
            Boolean declarouMaioridade,
            String versaoTermos
    ) {}
}
