package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlashcardRevisaoTest {
    final BaralhoRepository repo = mock(BaralhoRepository.class);
    final UsuarioAtualService atual = mock(UsuarioAtualService.class);
    final AnotacaoRepository notas = mock(AnotacaoRepository.class);
    final FlashcardService service = new FlashcardService(repo, atual, notas);
    Baralho deck;

    @BeforeEach
    void preparar() {
        var u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 1L);
        when(atual.obter()).thenReturn(u);
        deck = new Baralho();
        org.springframework.test.util.ReflectionTestUtils.setField(deck, "id", 2L);
        deck.setTitulo("Aula");
        deck.setAnotacaoId(3L);
        deck.setUsuario(u);
        deck.getFlashcards().add(new Flashcard("P", "R"));
        deck.getFlashcards().add(new Flashcard("P2", "R2"));
        when(repo.findByIdAndUsuarioId(2L, 1L)).thenReturn(Optional.of(deck));
        when(repo.findByUsuarioIdOrderByCriadoEmDesc(1L)).thenReturn(List.of(deck));
        when(repo.findByUsuarioIdAndAnotacaoIdOrderByCriadoEmDesc(1L, 3L)).thenReturn(List.of(deck));
        when(notas.findByIdAndUsuarioId(3L, 1L)).thenReturn(Optional.of(new Anotacao()));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void registraAcertosErrosERevisaSomenteCartoesComErro() {
        assertThat(service.registrarResultado(2L, 0, true).flashcards().get(0).acertos()).isEqualTo(1);
        assertThat(service.registrarResultado(2L, 1, false).flashcards().get(1).erros()).isEqualTo(1);
        assertThat(service.listar(3L).get(0).cartoesComErro()).isEqualTo(1);
        assertThat(service.listar().get(0).acertos()).isEqualTo(1);
        for (var result : List.of(service.revisarErros(null), service.revisarErros(3L), service.revisarErros(3L, 2L),
                service.revisarErros(null, 2L))) {
            assertThat(result).hasSize(1);
            var card = result.get(0);
            assertThat(card.baralhoId()).isEqualTo(2L);
            assertThat(card.indice()).isEqualTo(1);
            assertThat(card.pergunta()).isEqualTo("P2");
            assertThat(card.resposta()).isEqualTo("R2");
            assertThat(card.anotacaoId()).isEqualTo(3L);
            assertThat(card.titulo()).isEqualTo("Aula");
            assertThat(card.acertos()).isZero();
            assertThat(card.erros()).isEqualTo(1);
        }
    }

    @Test
    void indiceInvalidoNaoAlteraBaralho() {
        for (int indice : new int[] { -1, 2 })
            assertThatThrownBy(() -> service.registrarResultado(2L, indice, true))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void origemDeOutroUsuarioNaoPodeSerUsada() {
        assertThatThrownBy(() -> service.listar(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.revisarErros(99L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.revisarErros(99L, 2L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service
                .salvar(new FlashcardService.Pedido("Aula", 99L, List.of(new FlashcardService.Cartao("P", "R")))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        var semNotas = new FlashcardService(repo, atual);
        assertThatThrownBy(() -> semNotas.listar(3L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> semNotas.revisarErros(3L)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> semNotas
                .salvar(new FlashcardService.Pedido("Aula", 3L, List.of(new FlashcardService.Cartao("P", "R")))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void salvarVinculaOrigemValidaERemoveEspacos() {
        var result = service
                .salvar(new FlashcardService.Pedido(" Aula ", 3L, List.of(new FlashcardService.Cartao(" P ", " R "))));
        assertThat(result.titulo()).isEqualTo("Aula");
        assertThat(result.anotacaoId()).isEqualTo(3L);
        assertThat(result.flashcards().get(0).pergunta()).isEqualTo("P");
        var captor = org.mockito.ArgumentCaptor.forClass(Baralho.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isSameAs(atual.obter());
    }

    @Test
    void recusaCartaoNuloETextosInvalidos() {
        assertThatThrownBy(() -> service
                .salvar(new FlashcardService.Pedido("Aula", Arrays.asList((FlashcardService.Cartao) null))))
                .isInstanceOf(IllegalArgumentException.class);
        for (String texto : Arrays.asList(null, " ", "a".repeat(121))) {
            assertThatThrownBy(() -> service
                    .salvar(new FlashcardService.Pedido(texto, List.of(new FlashcardService.Cartao("P", "R")))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
