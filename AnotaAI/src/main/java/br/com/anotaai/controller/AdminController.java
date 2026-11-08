package br.com.anotaai.controller;

import br.com.anotaai.model.RegistroAuditoria;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.BaralhoRepository;
import br.com.anotaai.repository.RegistroAuditoriaRepository;
import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import br.com.anotaai.repository.RegistroUsoIaRepository;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.AdminService;
import br.com.anotaai.service.GmailApiEmailService;
import br.com.anotaai.service.UsuarioAtualService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UsuarioRepository usuarioRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final BaralhoRepository baralhoRepository;
    private final RegistroAuditoriaRepository auditoriaRepository;
    private final SolicitacaoExclusaoRepository solicitacaoExclusaoRepository;
    private final RegistroUsoIaRepository registroUsoIaRepository;
    private final AuditoriaService auditoriaService;
    private final AdminService adminService;
    private final UsuarioAtualService usuarioAtualService;
    private final GmailApiEmailService emailService;

    public AdminController(UsuarioRepository usuarioRepository,
                           AnotacaoRepository anotacaoRepository,
                           BaralhoRepository baralhoRepository,
                           RegistroAuditoriaRepository auditoriaRepository,
                           SolicitacaoExclusaoRepository solicitacaoExclusaoRepository,
                           RegistroUsoIaRepository registroUsoIaRepository,
                           AuditoriaService auditoriaService,
                           AdminService adminService,
                           UsuarioAtualService usuarioAtualService,
                           GmailApiEmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.baralhoRepository = baralhoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.solicitacaoExclusaoRepository = solicitacaoExclusaoRepository;
        this.registroUsoIaRepository = registroUsoIaRepository;
        this.auditoriaService = auditoriaService;
        this.adminService = adminService;
        this.usuarioAtualService = usuarioAtualService;
        this.emailService = emailService;
    }

    @GetMapping("/resumo")
    public Map<String, Long> resumo(@RequestParam(required = false) LocalDate inicio,
                                    @RequestParam(required = false) LocalDate fim) {
        var periodoAplicado = inicio != null && fim != null;
        var inicioPeriodo = periodoAplicado ? inicio.atStartOfDay() : null;
        var fimPeriodo = periodoAplicado ? fim.plusDays(1).atStartOfDay().minusNanos(1) : null;
        var usuarios = usuarioRepository.findAll();
        var anotacoes = anotacaoRepository.findAll();
        var todosBaralhos = baralhoRepository.findAll();
        var usuariosPeriodo = usuarios.stream().filter(u -> dentroDoPeriodo(u.getCriadoEm(), inicioPeriodo, fimPeriodo)).toList();
        var anotacoesPeriodo = anotacoes.stream().filter(a -> dentroDoPeriodo(a.getCriadoEm(), inicioPeriodo, fimPeriodo)).toList();
        var baralhosPeriodo = todosBaralhos.stream().filter(b -> dentroDoPeriodo(b.getCriadoEm(), inicioPeriodo, fimPeriodo)).toList();
        long flashcards = baralhosPeriodo.stream().mapToLong(b -> b.getFlashcards() == null ? 0 : b.getFlashcards().size()).sum();
        long ativos = usuariosPeriodo.stream().filter(u -> u.isAtivo()).count();
        long registrosAuditoria = periodoAplicado
                ? auditoriaRepository.countByCriadoEmBetween(inicioPeriodo, fimPeriodo)
                : auditoriaRepository.count();
        long falhasAuditoria = periodoAplicado
                ? auditoriaRepository.countBySucessoFalseAndCriadoEmBetween(inicioPeriodo, fimPeriodo)
                : auditoriaRepository.countBySucessoFalse();
        long usosIa = periodoAplicado
                ? registroUsoIaRepository.countByCriadoEmBetween(inicioPeriodo, fimPeriodo)
                : registroUsoIaRepository.count();
        return Map.ofEntries(
                Map.entry("usuarios", (long) usuariosPeriodo.size()),
                Map.entry("usuariosAtivos", ativos),
                Map.entry("usuariosDesativados", (long) usuariosPeriodo.size() - ativos),
                Map.entry("administradores", usuariosPeriodo.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getPerfil())).count()),
                Map.entry("novosUltimos7Dias", (long) usuariosPeriodo.size()),
                Map.entry("anotacoes", (long) anotacoesPeriodo.size()),
                Map.entry("baralhos", (long) baralhosPeriodo.size()),
                Map.entry("flashcards", flashcards),
                Map.entry("registrosAuditoria", registrosAuditoria),
                Map.entry("falhasAuditoria", falhasAuditoria),
                Map.entry("usosIa", usosIa)
        );
    }

    private boolean dentroDoPeriodo(LocalDateTime data, LocalDateTime inicio, LocalDateTime fim) {
        return inicio == null || (data != null && !data.isBefore(inicio) && !data.isAfter(fim));
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        Set<Long> pedidosPendentes = solicitacaoExclusaoRepository.findAll().stream()
                .map(pedido -> pedido.getUsuarioId()).collect(Collectors.toSet());
        return usuarioRepository.findAll().stream().map(usuario -> Map.<String, Object>of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "perfil", usuario.getPerfil(),
                "ativo", usuario.isAtivo(),
                "criadoEm", usuario.getCriadoEm(),
                "anotacoes", anotacaoRepository.countByUsuarioId(usuario.getId()),
                "pedidoExclusao", pedidosPendentes.contains(usuario.getId())
        )).toList();
    }

    @GetMapping("/pedidos-exclusao")
    public List<Map<String, Object>> pedidosExclusao() {
        return solicitacaoExclusaoRepository.findAllByOrderBySolicitadaEmAsc().stream().map(pedido -> Map.<String, Object>of(
                "id", pedido.getId(),
                "usuarioId", pedido.getUsuarioId(),
                "nome", pedido.getNome(),
                "email", pedido.getEmail(),
                "solicitadaEm", pedido.getSolicitadaEm()
        )).toList();
    }

    @DeleteMapping("/pedidos-exclusao/{id}")
    public Map<String, Object> concluirExclusao(@PathVariable Long id, HttpServletRequest request) {
        var administrador = usuarioAtualService.obter();
        var pedido = solicitacaoExclusaoRepository.findById(id)
                .orElseThrow(() -> new br.com.anotaai.service.RecursoNaoEncontradoException("Pedido de exclusão não encontrado."));
        adminService.excluir(pedido.getUsuarioId(), administrador);
        solicitacaoExclusaoRepository.delete(pedido);
        auditoriaService.registrar(administrador.getEmail(), "EXCLUSAO_CONTA_SOLICITADA_CONCLUIDA",
                "/api/admin/pedidos-exclusao/" + id, true,
                "Exclusão da conta solicitada por " + pedido.getEmail() + " concluída.", request);
        try {
            emailService.enviarConfirmacaoExclusao(pedido.getEmail(), pedido.getNome());
            return Map.of("mensagem", "Conta e dados excluídos. A confirmação foi enviada ao e-mail cadastrado.",
                    "emailEnviado", true);
        } catch (RuntimeException exception) {
            auditoriaService.registrar(administrador.getEmail(), "FALHA_ENVIO_CONFIRMACAO_EXCLUSAO",
                    "/api/admin/pedidos-exclusao/" + id, false,
                    "Conta excluída; falha ao enviar confirmação para o endereço cadastrado.", request);
            return Map.of("mensagem", "Conta e dados excluídos, mas não foi possível enviar a confirmação por e-mail.",
                    "emailEnviado", false);
        }
    }

    @PatchMapping("/usuarios/{id}/status")
    public Map<String, Object> alternarStatus(@PathVariable Long id, HttpServletRequest request) {
        if (solicitacaoExclusaoRepository.findByUsuarioId(id).isPresent()) {
            throw new IllegalStateException("A conta tem um pedido de exclusão pendente e não pode ser reativada.");
        }
        var administrador = usuarioAtualService.obter();
        var usuario = adminService.alternarAtivo(id, administrador);
        auditoriaService.registrar(administrador.getEmail(), "ALTERACAO_STATUS_USUARIO",
                "/api/admin/usuarios/" + id + "/status", true,
                "Usuário " + usuario.getEmail() + (usuario.isAtivo() ? " ativado." : " desativado."), request);
        return Map.of("id", usuario.getId(), "ativo", usuario.isAtivo(),
                "mensagem", usuario.isAtivo() ? "Usuário ativado com sucesso." : "Usuário desativado com sucesso.");
    }

    @DeleteMapping("/usuarios/{id}")
    public Map<String, Object> excluirUsuario(@PathVariable Long id, HttpServletRequest request) {
        var pedido = solicitacaoExclusaoRepository.findByUsuarioId(id)
                .orElseThrow(() -> new IllegalStateException("A exclusão só pode ser concluída após um pedido do próprio usuário."));
        var administrador = usuarioAtualService.obter();
        adminService.excluir(id, administrador);
        solicitacaoExclusaoRepository.delete(pedido);
        auditoriaService.registrar(administrador.getEmail(), "EXCLUSAO_USUARIO",
                "/api/admin/usuarios/" + id, true, "Usuário excluído.", request);
        try {
            emailService.enviarConfirmacaoExclusao(pedido.getEmail(), pedido.getNome());
            return Map.of("id", id, "emailEnviado", true,
                    "mensagem", "Usuário e dados excluídos; a confirmação foi enviada ao e-mail cadastrado.");
        } catch (RuntimeException exception) {
            auditoriaService.registrar(administrador.getEmail(), "FALHA_ENVIO_CONFIRMACAO_EXCLUSAO",
                    "/api/admin/usuarios/" + id, false,
                    "Conta excluída; falha ao enviar confirmação para o endereço cadastrado.", request);
            return Map.of("id", id, "emailEnviado", false,
                    "mensagem", "Usuário e dados excluídos, mas não foi possível enviar a confirmação por e-mail.");
        }
    }

    @PutMapping("/usuarios/{id}/perfil")
    public Map<String, Object> alterarPerfil(@PathVariable Long id,
                                              @RequestBody PerfilRequest dados,
                                              HttpServletRequest request) {
        var administrador = usuarioAtualService.obter();
        try {
            var usuario = adminService.alterarPerfil(id, dados.perfil(), administrador);
            auditoriaService.registrar(administrador.getEmail(), "ALTERACAO_PERFIL_USUARIO",
                    "/api/admin/usuarios/" + id + "/perfil", true,
                    "Perfil de " + usuario.getEmail() + " alterado para " + usuario.getPerfil() + ".", request);
            return Map.of(
                    "id", usuario.getId(),
                    "email", usuario.getEmail(),
                    "perfil", usuario.getPerfil(),
                    "mensagem", "Perfil atualizado com sucesso."
            );
        } catch (RuntimeException exception) {
            auditoriaService.registrar(administrador.getEmail(), "ALTERACAO_PERFIL_USUARIO",
                    "/api/admin/usuarios/" + id + "/perfil", false, exception.getMessage(), request);
            throw exception;
        }
    }

    @GetMapping("/auditoria")
    public List<Map<String, Object>> auditoria() {
        return auditoriaService.listarTodos().stream().map(this::mapearAuditoria).toList();
    }

    private Map<String, Object> mapearAuditoria(RegistroAuditoria registro) {
        var dados = new LinkedHashMap<String, Object>();
        dados.put("id", registro.getId());
        dados.put("email", registro.getUsuario() == null || registro.getUsuario().getId() == null
                ? "Não identificado" : "Conta " + registro.getUsuario().getId());
        dados.put("acao", registro.getAcao());
        dados.put("recurso", registro.getRecurso());
        dados.put("sucesso", registro.isSucesso());
        dados.put("enderecoIp", "-");
        dados.put("criadoEm", registro.getCriadoEm());
        return dados;
    }

    public record PerfilRequest(String perfil) {}
}
