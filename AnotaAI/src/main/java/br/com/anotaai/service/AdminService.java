package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.BaralhoRepository;
import br.com.anotaai.repository.CodigoAutenticacaoRepository;
import br.com.anotaai.repository.CodigoRecuperacaoRepository;
import br.com.anotaai.repository.RegistroAuditoriaRepository;
import br.com.anotaai.repository.VersaoAnotacaoRepository;
import br.com.anotaai.repository.RegistroUsoIaRepository;
import br.com.anotaai.model.RegistroAuditoria;
import br.com.anotaai.model.RegistroUsoIa;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {
    private static final Set<String> PERFIS_PERMITIDOS = Set.of("USER", "ADMIN");

    private final UsuarioRepository usuarioRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final VersaoAnotacaoRepository versaoRepository;
    private final BaralhoRepository baralhoRepository;
    private final CodigoAutenticacaoRepository autenticacaoRepository;
    private final CodigoRecuperacaoRepository recuperacaoRepository;
    private final RegistroAuditoriaRepository auditoriaRepository;
    private final RegistroUsoIaRepository registroUsoIaRepository;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public AdminService(UsuarioRepository usuarioRepository, AnotacaoRepository anotacaoRepository,
            VersaoAnotacaoRepository versaoRepository, BaralhoRepository baralhoRepository,
            CodigoAutenticacaoRepository autenticacaoRepository, CodigoRecuperacaoRepository recuperacaoRepository,
            RegistroAuditoriaRepository auditoriaRepository, RegistroUsoIaRepository registroUsoIaRepository,
            MongoTemplate mongoTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.versaoRepository = versaoRepository;
        this.baralhoRepository = baralhoRepository;
        this.autenticacaoRepository = autenticacaoRepository;
        this.recuperacaoRepository = recuperacaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.registroUsoIaRepository = registroUsoIaRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public AdminService(UsuarioRepository usuarioRepository, AnotacaoRepository anotacaoRepository,
            VersaoAnotacaoRepository versaoRepository, BaralhoRepository baralhoRepository,
            CodigoAutenticacaoRepository autenticacaoRepository, CodigoRecuperacaoRepository recuperacaoRepository,
            RegistroAuditoriaRepository auditoriaRepository, RegistroUsoIaRepository registroUsoIaRepository) {
        this(usuarioRepository, anotacaoRepository, versaoRepository, baralhoRepository, autenticacaoRepository,
                recuperacaoRepository, auditoriaRepository, registroUsoIaRepository, null);
    }

    public AdminService(UsuarioRepository usuarioRepository) {
        this(usuarioRepository, null, null, null, null, null, null, null);
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
    public Usuario anonimizar(Long usuarioId, Usuario administradorAtual) {
        var usuario = alvo(usuarioId, administradorAtual);
        if (usuario.isAnonimizado())
            return usuario;

        if (anotacaoRepository != null && versaoRepository != null && baralhoRepository != null) {
            var anotacoes = anotacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
            for (var anotacao : anotacoes) {
                versaoRepository.deleteByAnotacaoId(anotacao.getId());
                baralhoRepository.deleteByAnotacaoId(anotacao.getId());
            }
            baralhoRepository.deleteByUsuarioId(usuarioId);
            anotacaoRepository.deleteByUsuarioId(usuarioId);
        } else if (mongoTemplate != null) {
            throw new IllegalStateException("Não foi possível excluir os dados de estudo: repositórios indisponíveis.");
        }

        usuario.setNome("Usuário anonimizado");
        usuario.setEmail("anonimo-" + UUID.randomUUID() + "@anonimo.invalid");
        usuario.setSenha(null);
        usuario.setPerfil("USER");
        usuario.setAtivo(false);
        usuario.setAnonimizado(true);
        usuario.setDataNascimento(null);
        usuario.setEscolaridade(null);
        usuario.setTermosAceitosEm(null);
        usuario.setMaioridadeDeclaradaEm(null);
        usuario.setVersaoTermos(null);
        usuario.setCriadoEm(null);

        if (mongoTemplate != null) {
            var porUsuario = Query.query(Criteria.where("usuario.$id").is(usuarioId));
            mongoTemplate.updateMulti(porUsuario, new Update().unset("usuario").unset("email").unset("detalhes")
                    .unset("enderecoIp").unset("userAgent"), RegistroAuditoria.class);
            mongoTemplate.updateMulti(Query.query(Criteria.where("usuarioId").is(usuarioId)),
                    new Update().unset("usuarioId"), RegistroUsoIa.class);
        }

        if (autenticacaoRepository != null)
            autenticacaoRepository.deleteByUsuarioId(usuarioId);
        if (recuperacaoRepository != null)
            recuperacaoRepository.deleteByUsuarioId(usuarioId);
        return usuarioRepository.save(usuario);
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