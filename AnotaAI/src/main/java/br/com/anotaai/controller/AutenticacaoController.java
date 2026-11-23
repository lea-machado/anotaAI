package br.com.anotaai.controller;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.model.SolicitacaoExclusao;
import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.AutenticacaoDoisFatoresService;
import br.com.anotaai.service.UsuarioAtualService;
import br.com.anotaai.service.UsuarioService;
import br.com.anotaai.service.SolicitacaoExclusaoService;
import br.com.anotaai.service.TermosService;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.anotaai.service.GmailApiEmailService;
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
import java.util.Optional;
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
        private final SolicitacaoExclusaoService solicitacaoExclusaoService;
        private final GmailApiEmailService emailService;
        @Autowired
        private TermosService termosService;

        public AutenticacaoController(AuthenticationManager authenticationManager,
                        UsuarioAtualService usuarioAtualService,
                        AuditoriaService auditoriaService,
                        AutenticacaoDoisFatoresService doisFatoresService,
                        UsuarioRepository usuarioRepository,
                        UsuarioService usuarioService,
                        SolicitacaoExclusaoService solicitacaoExclusaoService,
                        GmailApiEmailService emailService) {
                this.authenticationManager = authenticationManager;
                this.usuarioAtualService = usuarioAtualService;
                this.auditoriaService = auditoriaService;
                this.doisFatoresService = doisFatoresService;
                this.usuarioRepository = usuarioRepository;
                this.usuarioService = usuarioService;
                this.solicitacaoExclusaoService = solicitacaoExclusaoService;
                this.emailService = emailService;
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
                                        "mensagem", "Codigo de verificacao enviado por e-mail."));
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
                        var usuarioValidado = doisFatoresService.validar(dados.desafioId(), dados.codigo());
                        var usuario = usuarioRepository.findById(usuarioValidado.getId())
                                        .orElseThrow(() -> new IllegalArgumentException("Conta não encontrada."));
                        Optional<SolicitacaoExclusao> pedidoPendente = usuario.isAtivo() ? Optional.empty()
                                        : solicitacaoExclusaoService.pedidoVigente(usuario.getId());
                        if (!usuario.isAtivo() && pedidoPendente.isEmpty())
                                throw new IllegalArgumentException("Esta conta está desativada ou o prazo expirou.");
                        var principal = org.springframework.security.core.userdetails.User
                                        .withUsername(usuario.getEmail())
                                        .password(usuario.getSenha())
                                        .roles(usuario.getPerfil())
                                        .build();
                        var authentication = new UsernamePasswordAuthenticationToken(
                                        principal, null, principal.getAuthorities());
                        criarSessaoAutenticada(authentication, request);
                        if (pedidoPendente.isPresent()) {
                                request.getSession().setAttribute("EXCLUSAO_RECUPERACAO_USUARIO_ID", usuario.getId());
                                auditoriaService.registrar(usuario.getEmail(), "LOGIN_2FA_CONTA_EM_EXCLUSAO",
                                                "/api/auth/2fa/verify", true,
                                                "Verificação concluída com acesso restrito à recuperação.", request);
                                return ResponseEntity.ok(Map.of("contaEmExclusao", true,
                                                "exclusaoEm",
                                                SolicitacaoExclusaoService.dataExclusao(pedidoPendente.get()),
                                                "mensagem", "Acesso restrito à recuperação da conta."));
                        }
                        auditoriaService.registrar(usuario.getEmail(), "LOGIN_2FA_SUCESSO", "/api/auth/2fa/verify",
                                        true,
                                        "Autenticacao em dois fatores concluida.", request);
                        return ResponseEntity.ok(Map.of("mensagem", "Login realizado com sucesso.",
                                        "termosPendentes",
                                        termosService != null && !termosService.aceitouVigentes(usuario)));
                } catch (IllegalArgumentException exception) {
                        auditoriaService.registrar(null, "LOGIN_2FA_FALHA", "/api/auth/2fa/verify", false,
                                        exception.getMessage(), request);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("mensagem", exception.getMessage()));
                }
        }

        private void encerrarSessaoExistente(HttpServletRequest request) {
                var session = request.getSession(false);
                if (session != null)
                        session.invalidate();
                SecurityContextHolder.clearContext();
        }

        private void criarSessaoAutenticada(org.springframework.security.core.Authentication authentication,
                        HttpServletRequest request) {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                var session = request.getSession(true);
                if (!session.isNew())
                        request.changeSessionId();
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }

        @GetMapping("/me")
        public Map<String, Object> usuarioAtual() {
                return dadosDoPerfil(usuarioAtualService.obter());
        }

        private Map<String, Object> dadosDoPerfil(Usuario usuario) {
                var dados = new java.util.LinkedHashMap<String, Object>();
                dados.put("id", usuario.getIdPublico());
                dados.put("nome", usuario.getNome());
                dados.put("email", usuario.getEmail());
                dados.put("perfil", usuario.getPerfil());
                dados.put("escolaridade", usuario.getEscolaridade());
                dados.put("criadoEm", usuario.getCriadoEm());
                return dados;
        }

        @PutMapping("/me")
        public ResponseEntity<?> atualizarUsuarioAtual(@RequestBody AtualizarPerfilRequest dados,
                        HttpServletRequest request) {
                var usuarioAtual = usuarioAtualService.obter();
                try {
                        var usuario = usuarioService.atualizarPerfil(
                                        usuarioAtual, dados.nome(), dados.email(), dados.escolaridade(),
                                        dados.senhaAtual(), dados.novaSenha());
                        atualizarAutenticacaoDaSessao(usuario, request);
                        auditoriaService.registrar(usuario.getEmail(), "PERFIL_ATUALIZADO", "/api/auth/me", true,
                                        "Dados do perfil atualizados.", request);
                        return ResponseEntity.ok(dadosDoPerfil(usuario));
                } catch (IllegalArgumentException exception) {
                        auditoriaService.registrar(usuarioAtual.getEmail(), "PERFIL_ATUALIZACAO_FALHA", "/api/auth/me",
                                        false,
                                        exception.getMessage(), request);
                        return ResponseEntity.badRequest().body(Map.of("mensagem", exception.getMessage()));
                }
        }

        @PostMapping("/exclusao")
        public ResponseEntity<?> solicitarExclusao(HttpServletRequest request) {
                var usuario = usuarioAtualService.obter();
                try {
                        var solicitacao = solicitacaoExclusaoService.solicitar(usuario, request);
                        usuarioRepository.findAll().stream()
                                        .filter(admin -> admin.isAtivo() && "ADMIN".equalsIgnoreCase(admin.getPerfil()))
                                        .forEach(admin -> {
                                                try {
                                                        emailService.enviarAvisoPedidoExclusao(admin.getEmail(),
                                                                        usuario.getNome(),
                                                                        usuario.getEmail(),
                                                                        solicitacao.getSolicitadaEm());
                                                        auditoriaService.registrar(admin.getEmail(),
                                                                        "AVISO_PEDIDO_EXCLUSAO_ENVIADO",
                                                                        "/api/admin/pedidos-exclusao", true,
                                                                        "Aviso de pedido de exclusão enviado ao administrador.",
                                                                        request);
                                                } catch (RuntimeException ignored) {
                                                }
                                        });
                        var session = request.getSession(false);
                        if (session != null)
                                session.invalidate();
                        SecurityContextHolder.clearContext();
                        return ResponseEntity.accepted().body(Map.of("mensagem",
                                        "Pedido recebido. O acesso à sua conta foi encerrado. Seus dados serão excluídos após 15 dias. Durante esse prazo, você poderá recuperar a conta fazendo login novamente."));
                } catch (IllegalStateException exception) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                        .body(Map.of("mensagem", exception.getMessage()));
                }
        }

        @GetMapping("/exclusao/status")
        public Map<String, Object> statusExclusao() {
                var usuario = usuarioRepository.findByEmailIgnoreCase(
                                SecurityContextHolder.getContext().getAuthentication().getName())
                                .orElseThrow(() -> new IllegalStateException("Conta não encontrada."));
                var pedido = solicitacaoExclusaoService.pedidoVigente(usuario.getId())
                                .orElseThrow(() -> new IllegalStateException("A conta não está em exclusão."));
                return Map.of("contaEmExclusao", true,
                                "exclusaoEm", SolicitacaoExclusaoService.dataExclusao(pedido));
        }

        @PostMapping("/exclusao/recuperar")
        public Map<String, String> recuperarConta(HttpServletRequest request) {
                var email = SecurityContextHolder.getContext().getAuthentication().getName();
                var usuario = usuarioRepository.findByEmailIgnoreCase(email)
                                .orElseThrow(() -> new IllegalStateException("Conta não encontrada."));
                solicitacaoExclusaoService.recuperar(usuario.getId(), request);
                encerrarSessaoExistente(request);
                return Map.of("mensagem", "Sua conta foi recuperada com todos os dados. Entre novamente.");
        }

        private void atualizarAutenticacaoDaSessao(Usuario usuario, HttpServletRequest request) {
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
                if (session != null)
                        session.invalidate();
                SecurityContextHolder.clearContext();
                return Map.of("mensagem", "Sessao encerrada.");
        }

        public record LoginRequest(String email, String senha) {
        }

        public record VerificacaoRequest(UUID desafioId, String codigo) {
        }

        public record AtualizarPerfilRequest(String nome, String email, String escolaridade,
                        String senhaAtual, String novaSenha) {
        }
}