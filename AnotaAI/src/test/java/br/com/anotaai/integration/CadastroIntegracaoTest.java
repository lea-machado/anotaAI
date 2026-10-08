
package br.com.anotaai.integration;

import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deveCadastrarUsuarioPelaApiESalvarNoBanco() throws Exception {
        String email = "integracao-" + UUID.randomUUID()
                + "@teste.com";

        String senha = "senha1234";

        String json = """
                {
                  "nome": "Usuario Integracao",
                  "email": "%s",
                  "senha": "senha1234",
                  "dataNascimento": "2000-01-01",
                  "escolaridade": "Ensino Superior Incompleto",
                  "aceitouTermos": true,
                  "declarouMaioridade": true,
                  "versaoTermos": "2026-09-25"
                }
                """.formatted(email);

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )

        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.nome")
                .value("Usuario Integracao"))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.perfil").value("USER"))
        .andExpect(jsonPath("$.mensagem")
                .value("Conta criada com sucesso."));

        var usuarioSalvo = usuarioRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow();
        assertNotNull(usuarioSalvo.getId());
        assertEquals(
                "Usuario Integracao",
                usuarioSalvo.getNome()
        );
        assertEquals(
                "Ensino Superior Incompleto",
                usuarioSalvo.getEscolaridade()
        );
        assertEquals(
                "USER",
                usuarioSalvo.getPerfil()
        );
        assertEquals(
                "2026-09-25",
                usuarioSalvo.getVersaoTermos()
        );

        assertNotEquals(senha, usuarioSalvo.getSenha());
        assertTrue(
                passwordEncoder.matches(
                        senha,
                        usuarioSalvo.getSenha()
                )
        );
    }
    
        @Test
        void Retornar400QuandoMenorDeIdadeTentaCadastrar() throws Exception {
        String email = "menor-" + UUID.randomUUID() + "@teste.com";
        String json = """
                {
                "nome": "Menor",
                "email": "%s",
                "senha": "senha1234",
                "dataNascimento": "2015-01-01",
                "escolaridade": "Ensino Fundamental Completo",
                "aceitouTermos": true,
                "declarouMaioridade": true,
                "versaoTermos": "2026-09-25"
                }
                """.formatted(email);

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value(
                "E necessario ter pelo menos 18 anos para criar uma conta."
        ))
        .andExpect(jsonPath("$.momento").exists());

        assertTrue(
                usuarioRepository.findByEmailIgnoreCase(email).isEmpty()
        );
        }

        @Test
        void deveImpedirCadastroComEmailDuplicado() throws Exception {
        String email = "duplicado-" + UUID.randomUUID() + "@teste.com";
        String json = """
                {
                "nome": "E-mail Duplicado",
                "email": "%s",
                "senha": "senha1234",
                "dataNascimento": "2000-01-01",
                "escolaridade": "Ensino Superior Incompleto",
                "aceitouTermos": true,
                "declarouMaioridade": true,
                "versaoTermos": "2026-09-25"
                }
                """.formatted(email);

        long quantidadeAntes = usuarioRepository.count();

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isCreated());

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value(
                "Ja existe uma conta cadastrada com este e-mail."
        ));

        assertEquals(
                quantidadeAntes + 1,
                usuarioRepository.count()
        );
        }
}
