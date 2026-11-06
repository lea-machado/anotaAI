package br.com.anotaai.service;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.model.VersaoAnotacao;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.VersaoAnotacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnotacaoService {
    private final AnotacaoRepository anotacaoRepository;
    private final VersaoAnotacaoRepository versaoAnotacaoRepository;
    private final UsuarioAtualService usuarioAtualService;

    public AnotacaoService(AnotacaoRepository anotacaoRepository, VersaoAnotacaoRepository versaoAnotacaoRepository,
                           UsuarioAtualService usuarioAtualService) {
        this.anotacaoRepository = anotacaoRepository;
        this.versaoAnotacaoRepository = versaoAnotacaoRepository;
        this.usuarioAtualService = usuarioAtualService;
    }

    @Transactional(readOnly = true)
    public List<Anotacao> listar(String busca) {
        var usuarioId = usuarioAtualService.obter().getId();
        if (busca != null && !busca.isBlank()) {
            return anotacaoRepository.findByUsuarioIdAndTituloContainingIgnoreCaseOrderByAtualizadoEmDesc(usuarioId, busca.trim());
        }
        return anotacaoRepository.findByUsuarioIdOrderByAtualizadoEmDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Anotacao buscar(Long id) {
        return anotacaoRepository.findByIdAndUsuarioId(id, usuarioAtualService.obter().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anotacao nao encontrada."));
    }

    @Transactional
    public Anotacao criar(Anotacao dados, String revisaoIa, String conteudoOriginal) {
        var anotacao = new Anotacao();
        anotacao.setUsuario(usuarioAtualService.obter());
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        var salva = anotacaoRepository.save(anotacao);
        salvarVersao(salva, "ORIGINAL", conteudoOriginal == null
                ? salva.getConteudo() : validarTexto(conteudoOriginal, "Conteudo original"));
        salvarRevisao(salva, revisaoIa);
        return salva;
    }

    @Transactional
    public Anotacao atualizar(Long id, Anotacao dados, String revisaoIa) {
        var anotacao = buscar(id);
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        salvarVersao(anotacao, "EDITADA", anotacao.getConteudo());
        salvarRevisao(anotacao, revisaoIa);
        return anotacaoRepository.save(anotacao);
    }

    @Transactional
    public void excluir(Long id) {
        var anotacao = buscar(id);
        versaoAnotacaoRepository.deleteByAnotacaoId(id);
        anotacaoRepository.delete(anotacao);
    }

    @Transactional(readOnly = true)
    public List<VersaoAnotacao> listarVersoes(Long anotacaoId) {
        buscar(anotacaoId);
        return versaoAnotacaoRepository.findByAnotacaoIdOrderByCriadoEmAsc(anotacaoId);
    }

    private void salvarVersao(Anotacao anotacao, String tipo, String conteudo) {
        var versao = new VersaoAnotacao();
        versao.setAnotacao(anotacao);
        versao.setTipo(tipo);
        versao.setConteudo(conteudo);
        versaoAnotacaoRepository.save(versao);
    }

    private void salvarRevisao(Anotacao anotacao, String conteudo) {
        if (conteudo == null) return;
        if (conteudo.isBlank() || conteudo.length() > 100000) {
            throw new IllegalArgumentException("A revisão deve conter entre 1 e 100.000 caracteres.");
        }
        var versao = new VersaoAnotacao();
        versao.setAnotacao(anotacao);
        versao.setTipo("REVISADA_IA");
        versao.setConteudo(conteudo.trim());
        versaoAnotacaoRepository.save(versao);
    }

    private String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }
}
