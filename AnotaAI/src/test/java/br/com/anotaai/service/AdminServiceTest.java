package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminServiceTest {
    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final AdminService service = new AdminService(repository);

    @Test
    void administradorPodePromoverOutroUsuario() {
        var administrador = usuario(1L, "ADMIN");
        var usuario = usuario(2L, "USER");
        when(repository.findById(2L)).thenReturn(Optional.of(usuario));
        when(repository.save(usuario)).thenReturn(usuario);

        var atualizado = service.alterarPerfil(2L, "admin", administrador);

        assertEquals("ADMIN", atualizado.getPerfil());
        verify(repository).save(usuario);
    }

    @Test
    void administradorNaoPodeAlterarProprioPerfil() {
        var administrador = usuario(1L, "ADMIN");
        when(repository.findById(1L)).thenReturn(Optional.of(administrador));

        assertThrows(IllegalArgumentException.class,
                () -> service.alterarPerfil(1L, "USER", administrador));
        verify(repository, never()).save(administrador);
    }

    @Test
    void recusaPerfilDesconhecido() {
        assertThrows(IllegalArgumentException.class,
                () -> service.alterarPerfil(2L, "GERENTE", usuario(1L, "ADMIN")));
    }

    private Usuario usuario(Long id, String perfil) {
        var usuario = new Usuario();
        usuario.setPerfil(perfil);
        try {
            var campo = Usuario.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(usuario, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return usuario;
    }

    @Test
    void rejeitarIdDeUsuarioNulo() {
        var administrador = usuario(1L, "ADMIN");

        IllegalArgumentException excecao = assertThrows(
                IllegalArgumentException.class,
                () -> service.alterarPerfil(
                        null, "USER", administrador
                )
        );

        assertEquals(
                "Usuario e obrigatorio.",
                excecao.getMessage()
        );
        verifyNoInteractions(repository);
    }
}
