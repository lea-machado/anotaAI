package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UsuarioService usuarioService = new UsuarioService(usuarioRepository, passwordEncoder);

    @Test
    void exigeDataDeNascimento() {
        assertThrows(IllegalArgumentException.class, () -> cadastrar(null));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void recusaMenorDeDezoitoAnos() {
        assertThrows(IllegalArgumentException.class, () -> cadastrar(LocalDate.now().minusYears(18).plusDays(1)));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void permiteQuemCompletaDezoitoAnosHoje() {
        var nascimento = LocalDate.now().minusYears(18);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var usuario = cadastrar(nascimento);

        assertEquals(nascimento, usuario.getDataNascimento());
        verify(usuarioRepository).save(any(Usuario.class));
    }



    @Test
    void exigeEscolaridade() {
        assertThrows(IllegalArgumentException.class, () -> usuarioService.criarUsuarioComum(
                "Pessoa Adulta", "adulta@example.com", "senha1234", LocalDate.now().minusYears(20),
                ""));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void salvaEscolaridadeInformada() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var usuario = cadastrar(LocalDate.now().minusYears(20));

        assertEquals("Ensino Superior Incompleto", usuario.getEscolaridade());
    }

    private Usuario cadastrar(LocalDate dataNascimento) {
        return usuarioService.criarUsuarioComum(
                "Pessoa Adulta", "adulta@example.com", "senha1234", dataNascimento,
                "Ensino Superior Incompleto");
    }
}
