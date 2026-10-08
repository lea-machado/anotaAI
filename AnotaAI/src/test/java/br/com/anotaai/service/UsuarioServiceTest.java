package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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
        LocalDate nascimento = LocalDate.now().minusYears(18).plusDays(1);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class, () -> cadastrar(nascimento));

        assertEquals("E necessario ter pelo menos 18 anos para criar uma conta.", excecao.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
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
    void exigeDeclaracaoDeMaioridade() {
        assertThrows(IllegalArgumentException.class, () -> usuarioService.criarUsuarioComum(
                "Pessoa Adulta", "adulta@example.com", "senha1234", LocalDate.now().minusYears(20),
                "Ensino Superior Incompleto", true, false, UsuarioService.VERSAO_ATUAL_TERMOS));
        verifyNoInteractions(usuarioRepository);
    }


    @Test
    void exigeEscolaridade() {
        assertThrows(IllegalArgumentException.class, () -> usuarioService.criarUsuarioComum(
                "Pessoa Adulta", "adulta@example.com", "senha1234", LocalDate.now().minusYears(20),
                "", true, true, UsuarioService.VERSAO_ATUAL_TERMOS));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void salvaEscolaridadeInformada() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var usuario = cadastrar(LocalDate.now().minusYears(20));

        assertEquals("Ensino Superior Incompleto", usuario.getEscolaridade());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12", "1234", "1234567"})
    void rejeitaSenhasComMenosDeOitoCaracteres(String senha){
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class, () -> UsuarioService.validarSenha(senha));
        
        assertEquals(
            "A senha deve ter pelo menos 8 caracteres.", excecao.getMessage());}



    private Usuario cadastrar(LocalDate dataNascimento) {
        return usuarioService.criarUsuarioComum(
                "Pessoa Adulta", "adulta@example.com", "senha1234", dataNascimento,
                "Ensino Superior Incompleto", true, true, UsuarioService.VERSAO_ATUAL_TERMOS);
    }
}
