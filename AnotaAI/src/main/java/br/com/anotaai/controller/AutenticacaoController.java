package br.com.anotaai.controller;

import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.AutenticacaoDoisFatoresService;
import br.com.anotaai.service.UsuarioAtualService;
import br.com.anotaai.service.UsuarioService;
import br.com.anotaai.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {
    private final AuthenticationManager authenticationManager;
    private final UsuarioAtualService usuarioAtualService;
    private final AuditoriaService auditoriaService;
    private final AutenticacaoDoisFatoresService doisFatoresService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    public AutenticacaoController(AuthenticationManager authenticationManager,
                                  UsuarioAtualService usuarioAtualService,
                                  AuditoriaService auditoriaService,
                                  AutenticacaoDoisFatoresService doisFatoresService,
                                  UsuarioRepository usuarioRepository,
                                  UsuarioService usuarioService) {
        this.authenticationManager = authenticationManager;
        this.usuarioAtualService = usuarioAtualService;
        this.auditoriaService = auditoriaService;
        this.doisFatoresService = doisFatoresService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest dados, HttpServletRequest request) {
        encerrarSessaoExistente(request);
        var email = dados.email() == null ? "" : dados.email().trim().toLowerCase();
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dados.senha()));
            var usuario = usuarioRepository.findByEmailIgnoreCase(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));
            var desafio = doisFatoresService.criarDesafio(usuario);
            auditoriaService.registrar(email, "CODIGO_2FA_ENVIADO", "/api/auth/login", true,
                    "Codigo de verificacao enviado por e-mail.", request);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "doisFatoresNecessario", true,
                    "desafioId", desafio.id(),
                    "emailMascarado", desafio.emailMascarado(),
                    "expiraEmSegundos", desafio.expiraEmSegundos(),
                    "mensagem", "Codigo de verificacao enviado por e-mail."
            ));
        } catch (AuthenticationException exception) {
            auditoriaService.registrar(email, "LOGIN_FALHA", "/api/auth/login", false,
                    "Credenciais invalidas.", request);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("mensagem", "E-mail ou senha invalidos."));
        }
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verificarCodigo(@RequestBody VerificacaoRequest dados, HttpServletRequest request) {
        try {
            var usuario = doisFatoresService.validar(dados.desafioId(), dados.codigo());
            var principal = org.springframework.security.core.userdetails.User
                    .withUsername(usuario.getEmail())
                    .password(usuario.getSenha())
                    .roles(usuario.getPerfil())
                    .build();
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            criarSessaoAutenticada(authentication, request);
            auditoriaService.registrar(usuario.getEmail(), "LOGIN_2FA_SUCESSO", "/api/auth/2fa/verify", true,
                    "Autenticacao em dois fatores concluida.", request);
            return ResponseEntity.ok(Map.of("mensagem", "Login realizado com sucesso."));
        } catch (IllegalArgumentException exception) {
            auditoriaService.registrar(null, "LOGIN_2FA_FALHA", "/api/auth/2fa/verify", false,
                    exception.getMessage(), request);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("mensagem", exception.getMessage()));
        }
    }

    private void encerrarSessaoExistente(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    private void criarSessaoAutenticada(org.springframework.security.core.Authentication authentication,
                                         HttpServletRequest request) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        var session = request.getSession(true);
        if (!session.isNew()) request.changeSessionId();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    @GetMapping("/me")
    public Map<String, Object> usuarioAtual() {
        var usuario = usuarioAtualService.obter();
        return Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "perfil", usuario.getPerfil(),
                "escolaridade", usuario.getEscolaridade() == null ? "" : usuario.getEscolaridade(),
                "criadoEm", usuario.getCriadoEm()
        );
    }

    @PutMapping("/me")
    public ResponseEntity<?> atualizarUsuarioAtual(@RequestBody AtualizarPerfilRequest dados, HttpServletRequest request) {
        var usuarioAtual = usuarioAtualService.obter();
        try {
            var usuario = usuarioService.atualizarPerfil(
                    usuarioAtual, dados.nome(), dados.email(), dados.escolaridade());
            atualizarAutenticacaoDaSessao(usuario, request);
            auditoriaService.registrar(usuario.getEmail(), "PERFIL_ATUALIZADO", "/api/auth/me", true,
                    "Dados do perfil atualizados.", request);
            return ResponseEntity.ok(Map.of(
                    "id", usuario.getId(),
                    "nome", usuario.getNome(),
                    "email", usuario.getEmail(),
                    "perfil", usuario.getPerfil(),
                    "escolaridade", usuario.getEscolaridade(),
                    "criadoEm", usuario.getCriadoEm()
            ));
        } catch (IllegalArgumentException exception) {
            auditoriaService.registrar(usuarioAtual.getEmail(), "PERFIL_ATUALIZACAO_FALHA", "/api/auth/me", false,
                    exception.getMessage(), request);
            return ResponseEntity.badRequest().body(Map.of("mensagem", exception.getMessage()));
        }
    }

    private void atualizarAutenticacaoDaSessao(br.com.anotaai.model.Usuario usuario, HttpServletRequest request) {
        var principal = org.springframework.security.core.userdetails.User
                .withUsername(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(usuario.getPerfil())
                .build();
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        criarSessaoAutenticada(authentication, request);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        auditoriaService.registrar(authentication.getName(), "LOGOUT", "/api/auth/logout", true,
                "Sessao encerrada.", request);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("mensagem", "Sessao encerrada.");
    }

    public record LoginRequest(String email, String senha) {}
    public record VerificacaoRequest(UUID desafioId, String codigo) {}
    public record AtualizarPerfilRequest(String nome, String email, String escolaridade) {}
}
