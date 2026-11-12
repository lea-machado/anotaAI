package br.com.anotaai.service;

import br.com.anotaai.model.Baralho;
import br.com.anotaai.model.Flashcard;
import br.com.anotaai.repository.BaralhoRepository;
import br.com.anotaai.repository.AnotacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashcardService {
    public record Cartao(String pergunta, String resposta) {}
    public record Pedido(String titulo, Long anotacaoId, List<Cartao> flashcards) {
        public Pedido(String titulo, List<Cartao> flashcards) { this(titulo, null, flashcards); }
    }
    public record Resumo(Long id, String titulo, Long anotacaoId, LocalDateTime criadoEm, int quantidade) {}
    public record CartaoEstudo(int indice, String pergunta, String resposta, int acertos, int erros) {}
    public record Detalhe(Long id, String titulo, Long anotacaoId, LocalDateTime criadoEm, List<CartaoEstudo> flashcards) {}
    public record CartaoComErro(Long baralhoId, String titulo, Long anotacaoId, int indice,
                                String pergunta, String resposta, int erros) {}
    private final BaralhoRepository repository;
    private final UsuarioAtualService usuarioAtual;
    private final AnotacaoRepository anotacaoRepository;

    @Autowired
    public FlashcardService(BaralhoRepository repository, UsuarioAtualService usuarioAtual,
                            AnotacaoRepository anotacaoRepository) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
        this.anotacaoRepository = anotacaoRepository;
    }

    /** Constructor retained for isolated repository tests. */
    public FlashcardService(BaralhoRepository repository, UsuarioAtualService usuarioAtual) {
        this(repository, usuarioAtual, null);
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar() {
        return listar(null);
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(Long anotacaoId) {
        Long usuarioId = usuarioAtual.obter().getId();
        if (anotacaoId != null && (anotacaoRepository == null
                || anotacaoRepository.findByIdAndUsuarioId(anotacaoId, usuarioId).isEmpty())) {
            throw new RecursoNaoEncontradoException("Anotação não encontrada.");
        }
        var baralhos = anotacaoId == null
                ? repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)
                : repository.findByUsuarioIdAndAnotacaoIdOrderByCriadoEmDesc(usuarioId, anotacaoId);
        return baralhos.stream()
                .map(b -> new Resumo(b.getId(), b.getTitulo(), b.getAnotacaoId(), b.getCriadoEm(), b.getFlashcards().size())).toList();
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(Long id) { return detalhe(doUsuario(id)); }

    @Transactional
    public Detalhe salvar(Pedido pedido) {
        if (pedido == null || pedido.flashcards() == null || pedido.flashcards().isEmpty()
                || pedido.flashcards().size() > 20) {
            throw new IllegalArgumentException("Salve entre 1 e 20 flashcards por conjunto.");
        }
        var baralho = new Baralho();
        baralho.setTitulo(texto(pedido.titulo(), "Título", 120));
        var usuario = usuarioAtual.obter();
        baralho.setUsuario(usuario);
        if (pedido.anotacaoId() != null) {
            if (anotacaoRepository == null || anotacaoRepository.findByIdAndUsuarioId(pedido.anotacaoId(), usuario.getId()).isEmpty()) {
                throw new RecursoNaoEncontradoException("Anotação de origem não encontrada.");
            }
            baralho.setAnotacaoId(pedido.anotacaoId());
        }
        for (Cartao card : pedido.flashcards()) {
            if (card == null) throw new IllegalArgumentException("Flashcard inválido.");
            baralho.getFlashcards().add(new Flashcard(texto(card.pergunta(), "Pergunta", 4000),
                    texto(card.resposta(), "Resposta", 10000)));
        }
        return detalhe(repository.save(baralho));
    }

    @Transactional
    public void excluir(Long id) { repository.delete(doUsuario(id)); }

    @Transactional
    public Detalhe registrarResultado(Long id, int indice, boolean correto) {
        var baralho = doUsuario(id);
        if (indice < 0 || indice >= baralho.getFlashcards().size()) {
            throw new RecursoNaoEncontradoException("Flashcard não encontrado.");
        }
        baralho.getFlashcards().get(indice).registrarResultado(correto);
        return detalhe(repository.save(baralho));
    }

    @Transactional(readOnly = true)
    public List<CartaoComErro> revisarErros(Long anotacaoId) {
        Long usuarioId = usuarioAtual.obter().getId();
        List<Baralho> baralhos;
        if (anotacaoId == null) {
            baralhos = repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
        } else {
            if (anotacaoRepository == null || anotacaoRepository.findByIdAndUsuarioId(anotacaoId, usuarioId).isEmpty()) {
                throw new RecursoNaoEncontradoException("Anotação não encontrada.");
            }
            baralhos = repository.findByUsuarioIdAndAnotacaoIdOrderByCriadoEmDesc(usuarioId, anotacaoId);
        }
        return java.util.stream.IntStream.range(0, baralhos.size()).boxed()
                .flatMap(deckIndex -> {
                    Baralho deck = baralhos.get(deckIndex);
                    return java.util.stream.IntStream.range(0, deck.getFlashcards().size())
                            .mapToObj(cardIndex -> {
                                Flashcard card = deck.getFlashcards().get(cardIndex);
                                return card.getErros() > 0 && card.isPendenteRevisao()
                                        ? new CartaoComErro(deck.getId(), deck.getTitulo(), deck.getAnotacaoId(),
                                        cardIndex, card.getPergunta(), card.getResposta(), card.getErros())
                                        : null;
                            }).filter(java.util.Objects::nonNull);
                }).toList();
    }

    private Baralho doUsuario(Long id) {
        return repository.findByIdAndUsuarioId(id, usuarioAtual.obter().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conjunto de flashcards não encontrado."));
    }

    private Detalhe detalhe(Baralho b) {
        return new Detalhe(b.getId(), b.getTitulo(), b.getAnotacaoId(), b.getCriadoEm(),
                java.util.stream.IntStream.range(0, b.getFlashcards().size()).mapToObj(index -> {
                    var card = b.getFlashcards().get(index);
                    return new CartaoEstudo(index, card.getPergunta(), card.getResposta(), card.getAcertos(), card.getErros());
                }).toList());
    }

    private String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.length() > limite) {
            throw new IllegalArgumentException(campo + " é obrigatório e deve ter até " + limite + " caracteres.");
        }
        return valor.trim();
    }
}
