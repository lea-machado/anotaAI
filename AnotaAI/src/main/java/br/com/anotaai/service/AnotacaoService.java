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
    public Anotacao criar(Anotacao dados) {
        var anotacao = new Anotacao();
        anotacao.setUsuario(usuarioAtualService.obter());
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        var salva = anotacaoRepository.save(anotacao);
        salvarVersaoOriginal(salva);
        return salva;
    }

    @Transactional
    public Anotacao atualizar(Long id, Anotacao dados) {
        var anotacao = buscar(id);
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        salvarVersaoOriginal(anotacao);
        return anotacao;
    }

    @Transactional
    public void excluir(Long id) {
        anotacaoRepository.delete(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<VersaoAnotacao> listarVersoes(Long anotacaoId) {
        buscar(anotacaoId);
        return versaoAnotacaoRepository.findByAnotacaoIdOrderByCriadoEmAsc(anotacaoId);
    }

    private void salvarVersaoOriginal(Anotacao anotacao) {
        var versao = new VersaoAnotacao();
        versao.setAnotacao(anotacao);
        versao.setTipo("ORIGINAL");
        versao.setConteudo(anotacao.getConteudo());
        versaoAnotacaoRepository.save(versao);
    }

    private String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }
}
