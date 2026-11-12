package br.com.anotaai.service;

import br.com.anotaai.model.SolicitacaoExclusao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
                "Solicitação de exclusão recebida; acesso suspenso até a conclusão pelo administrador.", request);
        return solicitacao;
    }
}
