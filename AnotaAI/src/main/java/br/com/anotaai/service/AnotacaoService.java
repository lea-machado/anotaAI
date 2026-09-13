package br.com.anotaai.service;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.repository.AnotacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnotacaoService {
    private final AnotacaoRepository anotacaoRepository;

    public AnotacaoService(AnotacaoRepository anotacaoRepository) {
        this.anotacaoRepository = anotacaoRepository;
    }

    @Transactional(readOnly = true)
    public List<Anotacao> listar(String busca) {
        if (busca != null && !busca.isBlank()) {
            return anotacaoRepository.findByTituloContainingIgnoreCaseOrderByAtualizadoEmDesc(busca.trim());
        }
        return anotacaoRepository.findAllByOrderByAtualizadoEmDesc();
    }

    @Transactional(readOnly = true)
    public Anotacao buscar(Long id) {
        return anotacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anotacao nao encontrada."));
    }

    @Transactional
    public Anotacao criar(Anotacao dados) {
        var anotacao = new Anotacao();
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        return anotacaoRepository.save(anotacao);
    }

    @Transactional
    public Anotacao atualizar(Long id, Anotacao dados) {
        var anotacao = buscar(id);
        anotacao.setTitulo(validarTexto(dados.getTitulo(), "Titulo"));
        anotacao.setConteudo(validarTexto(dados.getConteudo(), "Conteudo"));
        return anotacao;
    }

    @Transactional
    public void excluir(Long id) {
        anotacaoRepository.delete(buscar(id));
    }

    private String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio.");
        }
        return valor.trim();
    }
}
