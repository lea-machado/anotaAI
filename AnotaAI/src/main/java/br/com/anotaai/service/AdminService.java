package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.model.SolicitacaoExclusao;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.BaralhoRepository;
import br.com.anotaai.repository.CodigoAutenticacaoRepository;
import br.com.anotaai.repository.CodigoRecuperacaoRepository;
import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import br.com.anotaai.repository.VersaoAnotacaoRepository;
import br.com.anotaai.model.RegistroAuditoria;
import br.com.anotaai.model.RegistroUsoIa;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class AdminService {
    private static final Set<String> PERFIS_PERMITIDOS = Set.of("USER", "ADMIN");

    private final UsuarioRepository usuarioRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final VersaoAnotacaoRepository versaoRepository;
    private final BaralhoRepository baralhoRepository;
    private final CodigoAutenticacaoRepository autenticacaoRepository;
    private final CodigoRecuperacaoRepository recuperacaoRepository;
    private final SolicitacaoExclusaoRepository solicitacaoExclusaoRepository;
    private final MongoTemplate mongoTemplate;

    public AdminService(UsuarioRepository usuarioRepository, AnotacaoRepository anotacaoRepository,
            VersaoAnotacaoRepository versaoRepository, BaralhoRepository baralhoRepository,
            CodigoAutenticacaoRepository autenticacaoRepository, CodigoRecuperacaoRepository recuperacaoRepository,
            SolicitacaoExclusaoRepository solicitacaoExclusaoRepository, MongoTemplate mongoTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.versaoRepository = versaoRepository;
        this.baralhoRepository = baralhoRepository;
        this.autenticacaoRepository = autenticacaoRepository;
        this.recuperacaoRepository = recuperacaoRepository;
        this.solicitacaoExclusaoRepository = solicitacaoExclusaoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional
    public Usuario alterarPerfil(Long usuarioId, String perfil, Usuario administradorAtual) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Usuario e obrigatorio.");
        }
        var perfilNormalizado = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        if (!PERFIS_PERMITIDOS.contains(perfilNormalizado)) {
            throw new IllegalArgumentException("Perfil deve ser USER ou ADMIN.");
        }

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));
        if (usuario.getId().equals(administradorAtual.getId())) {
            throw new IllegalArgumentException("Nao e permitido alterar o proprio perfil administrativo.");
        }
        if (usuario.isAnonimizado()) {
            throw new IllegalStateException("A conta anonimizada não pode ser alterada.");
        }

        usuario.setPerfil(perfilNormalizado);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario alternarAtivo(Long usuarioId, Usuario administradorAtual) {
        var usuario = alvo(usuarioId, administradorAtual);
        if (usuario.isAnonimizado()) {
            throw new IllegalStateException("A conta anonimizada não pode ser reativada.");
        }
        usuario.setAtivo(!usuario.isAtivo());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void excluirConta(SolicitacaoExclusao pedido, Usuario administradorAtual) {
        alvo(pedido.getUsuarioId(), administradorAtual);
        excluirContaVerificada(pedido);
    }

    @Transactional
    public void excluirContaAutomatica(SolicitacaoExclusao pedido) {
        excluirContaVerificada(pedido);
    }

    private void excluirContaVerificada(SolicitacaoExclusao pedido) {
        if (pedido.getSolicitadaEm() == null || SolicitacaoExclusaoService.vigente(pedido)) {
            throw new IllegalStateException("A exclusão só poderá ocorrer após os 15 dias de recuperação.");
        }
        var usuarioId = pedido.getUsuarioId();
        var pedidoAtual = solicitacaoExclusaoRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new IllegalStateException("A exclusão foi cancelada."));
        if (!java.util.Objects.equals(pedidoAtual.getId(), pedido.getId())
                || !java.util.Objects.equals(pedidoAtual.getSolicitadaEm(), pedido.getSolicitadaEm())) {
            throw new IllegalStateException("O pedido de exclusão foi substituído.");
        }
        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));
        if (usuario.isAtivo()) {
            throw new IllegalStateException("A conta está ativa e não pode ser excluída.");
        }
        for (var anotacao : anotacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)) {
            versaoRepository.deleteByAnotacaoId(anotacao.getId());
            baralhoRepository.deleteByAnotacaoId(anotacao.getId());
        }
        baralhoRepository.deleteByUsuarioId(usuarioId);
        anotacaoRepository.deleteByUsuarioId(usuarioId);
        autenticacaoRepository.deleteByUsuarioId(usuarioId);
        recuperacaoRepository.deleteByUsuarioId(usuarioId);

        var porUsuario = Query.query(Criteria.where("usuario.$id").is(usuarioId));
        mongoTemplate.updateMulti(porUsuario, new Update().unset("usuario").unset("email").unset("detalhes")
                .unset("enderecoIp").unset("userAgent"), RegistroAuditoria.class);
        mongoTemplate.updateMulti(Query.query(Criteria.where("usuarioId").is(usuarioId)),
                new Update().unset("usuarioId"), RegistroUsoIa.class);

        usuarioRepository.delete(usuario);
        solicitacaoExclusaoRepository.delete(pedido);
    }

    private Usuario alvo(Long usuarioId, Usuario administradorAtual) {
        if (usuarioId == null)
            throw new IllegalArgumentException("Usuario e obrigatorio.");
        if (usuarioId.equals(administradorAtual.getId())) {
            throw new IllegalArgumentException("Nao e permitido alterar ou excluir a propria conta administrativa.");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));
    }
}