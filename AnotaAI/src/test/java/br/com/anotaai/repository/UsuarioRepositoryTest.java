
package br.com.anotaai.repository;

import br.com.anotaai.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioRepositoryTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private UsuarioRepository usuarioRepository;

    @Test
    void buscarParaRecuperacao_delegaBuscaPorEmailIgnorandoCase() {
        Usuario usuario = new Usuario();
        when(usuarioRepository.findByEmailIgnoreCase("TESTE@TESTE.COM"))
                .thenReturn(Optional.of(usuario));

        Optional<Usuario> encontrado = usuarioRepository
                .buscarParaRecuperacao("TESTE@TESTE.COM");

        assertSame(usuario, encontrado.orElseThrow());
        verify(usuarioRepository).findByEmailIgnoreCase("TESTE@TESTE.COM");
    }
}
