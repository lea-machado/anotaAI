package br.com.anotaai.service;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.model.VersaoAnotacao;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.VersaoAnotacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnotacaoServiceTest {
    private final AnotacaoRepository notas = mock(AnotacaoRepository.class);
    private final VersaoAnotacaoRepository versoes = mock(VersaoAnotacaoRepository.class);
    private final UsuarioAtualService atual = mock(UsuarioAtualService.class);
    private final AnotacaoService service = new AnotacaoService(notas, versoes, atual);

    private Anotacao dados() {
        var nota = new Anotacao();
        nota.setTitulo("Biologia");
        nota.setConteudo("Texto original");
        return nota;
    }

    @Test
    void criaOriginalERevisaoSemSubstituirTextoDoUsuario() {
        var usuario = new Usuario();
        when(atual.obter()).thenReturn(usuario);
        when(notas.save(any())).thenAnswer(call -> call.getArgument(0));
        var salva = service.criar(dados(), "Resumo da IA", "Original antes da IA");
        assertEquals("Texto original", salva.getConteudo());
        assertSame(usuario, salva.getUsuario());
        var captor = ArgumentCaptor.forClass(VersaoAnotacao.class);
        verify(versoes, times(2)).save(captor.capture());
        assertEquals("ORIGINAL", captor.getAllValues().get(0).getTipo());
        assertEquals("Original antes da IA", captor.getAllValues().get(0).getConteudo());
        assertEquals("REVISADA_IA", captor.getAllValues().get(1).getTipo());
        assertEquals("Resumo da IA", captor.getAllValues().get(1).getConteudo());
        assertSame(salva, captor.getAllValues().get(1).getAnotacao());
    }

    @Test
    void salvarSemNovaRevisaoNaoDuplicaNemApagaHistoricoIa() {
        var usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(atual.obter()).thenReturn(usuario);
        when(notas.findByIdAndUsuarioId(42L, 7L)).thenReturn(Optional.of(dados()));
        service.atualizar(42L, dados(), null);
        var captor = ArgumentCaptor.forClass(VersaoAnotacao.class);
        verify(versoes).save(captor.capture());
        assertEquals("EDITADA", captor.getValue().getTipo());
        verify(versoes, never()).delete(any());
    }

    @Test
    void naoPermiteGravarOuLerRevisaoDeOutraConta() {
        var usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(atual.obter()).thenReturn(usuario);
        when(notas.findByIdAndUsuarioId(42L, 7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.atualizar(42L, dados(), "Resumo"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.listarVersoes(42L));
        verifyNoInteractions(versoes);
    }
}
