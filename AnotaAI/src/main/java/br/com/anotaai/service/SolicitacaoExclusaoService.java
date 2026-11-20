package br.com.anotaai.service;

import br.com.anotaai.model.SolicitacaoExclusao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SolicitacaoExclusaoService {
    private final SolicitacaoExclusaoRepository solicitacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public SolicitacaoExclusaoService(SolicitacaoExclusaoRepository solicitacaoRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    public static final int PRAZO_DIAS = 15;

    public static LocalDateTime dataExclusao(SolicitacaoExclusao pedido) {
        return pedido.getSolicitadaEm().plusDays(PRAZO_DIAS);
    }

    public static boolean vigente(SolicitacaoExclusao pedido) {
        return pedido != null && pedido.getSolicitadaEm() != null
                && LocalDateTime.now().isBefore(dataExclusao(pedido));
    }

    public Optional<SolicitacaoExclusao> pedidoVigente(Long usuarioId) {
        return solicitacaoRepository.findByUsuarioId(usuarioId).filter(SolicitacaoExclusaoService::vigente);
    }

    @Transactional
    public Usuario recuperar(Long usuarioId, jakarta.servlet.http.HttpServletRequest request) {
        var pedido = pedidoVigente(usuarioId)
                .orElseThrow(() -> new IllegalStateException("O prazo para recuperar esta conta terminou."));
        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Conta não encontrada."));
        if (usuario.isAtivo() || usuario.isAnonimizado()) {
            throw new IllegalStateException("Esta conta não está aguardando exclusão.");
        }
        usuario.setAtivo(true);
        usuarioRepository.save(usuario);
        solicitacaoRepository.delete(pedido);
        auditoriaService.registrar(usuario.getEmail(), "CANCELAMENTO_EXCLUSAO_CONTA",
                "/api/auth/exclusao/recuperar", true, "Exclusão cancelada pelo titular; conta reativada.", request);
        return usuario;
    }

    @Transactional
    public SolicitacaoExclusao solicitar(Usuario usuario, jakarta.servlet.http.HttpServletRequest request) {
        if (solicitacaoRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            throw new IllegalStateException("Já existe um pedido de exclusão para esta conta.");
        }
        var solicitacao = new SolicitacaoExclusao();
        solicitacao.setUsuarioId(usuario.getId());
        solicitacao.setNome(usuario.getNome());
        solicitacao.setEmail(usuario.getEmail());
        solicitacao.setSolicitadaEm(LocalDateTime.now());
        solicitacaoRepository.save(solicitacao);

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(usuario.getEmail(), "SOLICITACAO_EXCLUSAO_CONTA", "/api/auth/exclusao", true,
                "Solicitação de exclusão recebida; acesso suspenso até a exclusão automática em 15 dias.", request);
        return solicitacao;
    }
}