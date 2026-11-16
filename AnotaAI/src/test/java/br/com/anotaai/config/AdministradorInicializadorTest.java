package br.com.anotaai.config;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministradorInicializadorTest {
    private static final String EMAIL = "bootstrap@example.com";
    private static final String SENHA = "Conta!Forte2026";
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void criaAdministradorComEmailConfiguradoESenhaCodificada() {
        when(encoder.encode(SENHA)).thenReturn("hash");
        new AdministradorInicializador(repo, encoder, false, EMAIL, SENHA).run();

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).findByEmailIgnoreCase(EMAIL);
        verify(repo).save(captor.capture());
        var administrador = captor.getValue();
        assertThat(administrador.getEmail()).isEqualTo(EMAIL);
        assertThat(administrador.getPerfil()).isEqualTo("ADMIN");
        assertThat(administrador.getSenha()).isEqualTo("hash");
        assertThat(administrador.isAtivo()).isTrue();
    }

    @Test
    void semEmailConfiguradoNaoInicializaAdministrador() {
        new AdministradorInicializador(repo, encoder, false, "", "").run();
        verifyNoInteractions(repo, encoder);
    }

    @Test
    void senhaInvalidaNaoCriaConta() {
        assertThatThrownBy(() -> new AdministradorInicializador(repo, encoder, false, EMAIL, "").run())
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void administradorExistentePreservaSenhaSalvoRedefinicaoExplicita() {
        var administrador = new Usuario();
        administrador.setPerfil("ADMIN");
        administrador.setSenha("antiga");
        when(repo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(administrador));

        new AdministradorInicializador(repo, encoder, false, EMAIL, "").run();
        assertThat(administrador.getSenha()).isEqualTo("antiga");
        verify(repo, never()).save(any());

        when(encoder.encode(SENHA)).thenReturn("nova");
        new AdministradorInicializador(repo, encoder, true, EMAIL, SENHA).run();
        assertThat(administrador.getSenha()).isEqualTo("nova");
        verify(repo).save(administrador);
    }

    @Test
    void rejeitaEmailJaUsadoPorContaComum() {
        var conta = new Usuario();
        conta.setPerfil("USER");
        when(repo.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(conta));

        assertThatThrownBy(() -> new AdministradorInicializador(repo, encoder, true, EMAIL, SENHA).run())
                .isInstanceOf(IllegalStateException.class);
        assertThat(conta.getPerfil()).isEqualTo("USER");
        verify(repo, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
