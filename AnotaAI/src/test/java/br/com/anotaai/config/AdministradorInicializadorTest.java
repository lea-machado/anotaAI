package br.com.anotaai.config;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministradorInicializadorTest {
    final UsuarioRepository repo = mock(UsuarioRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final String senha = "Conta!Forte2026";
    final String email = "admin@exemplo.com";

    @Test
    void bootstrapDesativadoSemEmailNaoCriaConta() {
        new AdministradorInicializador(repo, encoder, false, "", "").run();
        verifyNoInteractions(repo, encoder);
    }

    @Test
    void criaAdministradorComEmailConfiguradoESenhaCodificada() {
        when(encoder.encode(senha)).thenReturn("hash");
        new AdministradorInicializador(repo, encoder, false, email, senha).run();
        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(email);
        assertThat(captor.getValue().getPerfil()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getSenha()).isEqualTo("hash");
        assertThat(captor.getValue().isAtivo()).isTrue();
    }

    @Test
    void senhaInvalidaNaoCriaConta() {
        assertThatThrownBy(() -> new AdministradorInicializador(repo, encoder, false, email, "").run())
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void administradorExistentePreservaSenhaSalvoRedefinicaoExplicita() {
        var u = new Usuario();
        u.setPerfil("ADMIN");
        u.setSenha("antiga");
        when(repo.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        new AdministradorInicializador(repo, encoder, false, email, "").run();
        assertThat(u.getSenha()).isEqualTo("antiga");
        verify(repo, never()).save(any());
        when(encoder.encode(senha)).thenReturn("nova");
        new AdministradorInicializador(repo, encoder, true, email, senha).run();
        assertThat(u.getSenha()).isEqualTo("nova");
        verify(repo).save(u);
    }

    @Test
    void naoPromoveUsuarioComumParaAdmin() {
        var u = new Usuario();
        u.setPerfil("USER");
        when(repo.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> new AdministradorInicializador(repo, encoder, false, email, senha).run())
                .isInstanceOf(IllegalStateException.class);
        assertThat(u.getPerfil()).isEqualTo("USER");
        verify(repo, never()).save(any());
        verifyNoInteractions(encoder);
    }
}