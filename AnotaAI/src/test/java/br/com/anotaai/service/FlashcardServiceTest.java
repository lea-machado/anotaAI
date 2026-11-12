package br.com.anotaai.service;

import br.com.anotaai.model.Baralho;
import br.com.anotaai.model.Flashcard;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.BaralhoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlashcardServiceTest {
    private final BaralhoRepository repository = mock(BaralhoRepository.class);
    private final UsuarioAtualService atual = mock(UsuarioAtualService.class);
    private final Usuario usuario = mock(Usuario.class);
    private final FlashcardService service = new FlashcardService(repository, atual);

    @BeforeEach
    void usuarioAutenticado() {
        when(atual.obter()).thenReturn(usuario);
        when(usuario.getId()).thenReturn(7L);
    }

    @Test
    void salvaCartoesNaContaAutenticadaPreservandoOrdem() {
        when(repository.save(any())).thenAnswer(invocation -> {
            Baralho salvo = invocation.getArgument(0);
            assertSame(usuario, salvo.getUsuario());
            return salvo;
        });
        var resultado = service.salvar(new FlashcardService.Pedido(" Biologia ", List.of(
                new FlashcardService.Cartao(" Pergunta 1 ", " Resposta 1 "),
                new FlashcardService.Cartao("Pergunta 2", "Resposta 2"))));
        assertEquals("Biologia", resultado.titulo());
        assertEquals("Pergunta 1", resultado.flashcards().get(0).pergunta());
        assertEquals("Resposta 2", resultado.flashcards().get(1).resposta());
        verify(repository).save(any());
    }

    @Test
    void listaApenasConjuntosDoUsuario() {
        var baralho = new Baralho();
        baralho.setTitulo("Matemática");
        baralho.getFlashcards().add(new Flashcard("Pergunta", "Resposta"));
        when(repository.findByUsuarioIdOrderByCriadoEmDesc(7L)).thenReturn(List.of(baralho));
        var lista = service.listar();
        assertEquals(1, lista.size());
        assertEquals(1, lista.get(0).quantidade());
        verify(repository, never()).findAll();
    }

    @Test
    void naoPermiteLerOuExcluirConjuntoDeOutraConta() {
        when(repository.findByIdAndUsuarioId(42L, 7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(42L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(42L));
        verify(repository, never()).delete(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void consultaEExcluiConjuntoProprio() {
        var baralho = new Baralho();
        baralho.setTitulo("História");
        baralho.getFlashcards().add(new Flashcard("Quando?", "Na data da anotação"));
        when(repository.findByIdAndUsuarioId(42L, 7L)).thenReturn(Optional.of(baralho));
        assertEquals("Quando?", service.buscar(42L).flashcards().get(0).pergunta());
        service.excluir(42L);
        verify(repository).delete(baralho);
    }

    @Test
    void rejeitaDadosInvalidosSemSalvar() {
        var card = new FlashcardService.Cartao("Pergunta", "Resposta");
        assertThrows(IllegalArgumentException.class, () -> service.salvar(null));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("Título", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("Título", Collections.nCopies(21, card))));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido(" ", List.of(card))));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("a".repeat(121), List.of(card))));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("Título", List.of(new FlashcardService.Cartao("P", " ")))));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("Título", List.of(new FlashcardService.Cartao("a".repeat(4001), "R")))));
        assertThrows(IllegalArgumentException.class, () -> service.salvar(new FlashcardService.Pedido("Título", Collections.singletonList(null))));
        verify(repository, never()).save(any());
    }
}
