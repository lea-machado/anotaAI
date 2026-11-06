package br.com.anotaai.service;

import br.com.anotaai.model.Baralho;
import br.com.anotaai.model.Flashcard;
import br.com.anotaai.repository.BaralhoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashcardService {
    public record Cartao(String pergunta, String resposta) {}
    public record Pedido(String titulo, List<Cartao> flashcards) {}
    public record Resumo(Long id, String titulo, LocalDateTime criadoEm, int quantidade) {}
    public record Detalhe(Long id, String titulo, LocalDateTime criadoEm, List<Cartao> flashcards) {}
    private final BaralhoRepository repository;
    private final UsuarioAtualService usuarioAtual;

    public FlashcardService(BaralhoRepository repository, UsuarioAtualService usuarioAtual) {
        this.repository = repository;
        this.usuarioAtual = usuarioAtual;
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar() {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioAtual.obter().getId()).stream()
                .map(b -> new Resumo(b.getId(), b.getTitulo(), b.getCriadoEm(), b.getFlashcards().size())).toList();
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(Long id) { return detalhe(doUsuario(id)); }

    @Transactional
    public Detalhe salvar(Pedido pedido) {
        if (pedido == null || pedido.flashcards() == null || pedido.flashcards().isEmpty()
                || pedido.flashcards().size() > 10) {
            throw new IllegalArgumentException("Salve entre 1 e 10 flashcards por conjunto.");
        }
        var baralho = new Baralho();
        baralho.setTitulo(texto(pedido.titulo(), "Título", 120));
        baralho.setUsuario(usuarioAtual.obter());
        for (Cartao card : pedido.flashcards()) {
            if (card == null) throw new IllegalArgumentException("Flashcard inválido.");
            baralho.getFlashcards().add(new Flashcard(texto(card.pergunta(), "Pergunta", 4000),
                    texto(card.resposta(), "Resposta", 10000)));
        }
        return detalhe(repository.save(baralho));
    }

    @Transactional
    public void excluir(Long id) { repository.delete(doUsuario(id)); }

    private Baralho doUsuario(Long id) {
        return repository.findByIdAndUsuarioId(id, usuarioAtual.obter().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conjunto de flashcards não encontrado."));
    }

    private Detalhe detalhe(Baralho b) {
        return new Detalhe(b.getId(), b.getTitulo(), b.getCriadoEm(), b.getFlashcards().stream()
                .map(c -> new Cartao(c.getPergunta(), c.getResposta())).toList());
    }

    private String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.length() > limite) {
            throw new IllegalArgumentException(campo + " é obrigatório e deve ter até " + limite + " caracteres.");
        }
        return valor.trim();
    }
}
