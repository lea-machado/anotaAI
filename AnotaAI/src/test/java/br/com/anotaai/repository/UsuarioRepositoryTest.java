
package br.com.anotaai.repository;

import br.com.anotaai.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deveSalvarERecuperarUsuarioPeloEmail() {

        Usuario usuario = new Usuario();
        usuario.setNome("Teste");
        usuario.setEmail("teste@teste.com");
        usuario.setSenha("minhasenha");
        usuario.setPerfil("USER");
        usuario.setDataNascimento(
                LocalDate.of(2000, 1, 1)
        );
        usuario.setEscolaridade(
                "Ensino Superior Incompleto"
        );

        Usuario usuarioSalvo =
                usuarioRepository.saveAndFlush(usuario);
        Long idGerado = usuarioSalvo.getId();


        entityManager.clear();


        Usuario usuarioEncontrado = usuarioRepository
                .findByEmailIgnoreCase(
                        "TESTE@TESTE.COM"
                )
                .orElseThrow();

        assertNotNull(idGerado);
        assertEquals(
                idGerado,
                usuarioEncontrado.getId()
        );

        assertEquals(
                "Teste",
                usuarioEncontrado.getNome()
        );

        assertEquals(
                "teste@teste.com",
                usuarioEncontrado.getEmail()
        );

        assertEquals(
                "USER",
                usuarioEncontrado.getPerfil()
        );

        assertNotNull(
                usuarioEncontrado.getCriadoEm()
        );
    }
}
