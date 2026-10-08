
package br.com.anotaai.controller;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadastroControllerTest {

    @Test
    void retornar201QuandoCadastroForValido() throws Exception {
        UsuarioService usuarioService = mock(UsuarioService.class);
        AuditoriaService auditoriaService = mock(AuditoriaService.class);
        Usuario usuario = new Usuario();
        usuario.setNome("Teste");
        usuario.setEmail("teste@teste.com");
        usuario.setPerfil("USER");
        var campoId = Usuario.class.getDeclaredField("id");
        campoId.setAccessible(true);
        campoId.set(usuario, 1L);
        when(usuarioService.criarUsuarioComum(
                anyString(),
                anyString(),
                anyString(),
                any(LocalDate.class),
                anyString(),
                anyBoolean(),
                anyBoolean(),
                anyString()
        )).thenReturn(usuario);
        CadastroController controller =
                new CadastroController(usuarioService, auditoriaService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        String json = """
                {
                  "nome": "Teste",
                  "email": "teste@teste.com",
                  "senha": "senha1234",
                  "dataNascimento": "2000-01-01",
                  "escolaridade": "Ensino Superior Incompleto",
                  "aceitouTermos": true,
                  "declarouMaioridade": true,
                  "versaoTermos": "2026-09-25"
                }
                """;

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.nome").value("Teste"))
        .andExpect(jsonPath("$.email").value("teste@teste.com"))
        .andExpect(jsonPath("$.mensagem")
                .value("Conta criada com sucesso."));
        verify(usuarioService).criarUsuarioComum(
                anyString(),
                anyString(),
                anyString(),
                any(LocalDate.class),
                anyString(),
                anyBoolean(),
                anyBoolean(),
                anyString()
        );
    }
    
        @Test
        void retornar400QuandoUsuarioForMenorDeIdade() throws Exception {
        UsuarioService usuarioService = mock(UsuarioService.class);
        AuditoriaService auditoriaService = mock(AuditoriaService.class);
        when(usuarioService.criarUsuarioComum(
                anyString(),
                anyString(),
                anyString(),
                any(LocalDate.class),
                anyString(),
                anyBoolean(),
                anyBoolean(),
                anyString()
        )).thenThrow(
                new IllegalArgumentException(
                        "E necessario ter pelo menos 18 anos para criar uma conta."
                )
        );
        CadastroController controller =
                new CadastroController(usuarioService, auditoriaService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        String json = """
                {
                "nome": "Menor",
                "email": "menor@teste.com",
                "senha": "senha1234",
                "dataNascimento": "2015-01-01",
                "escolaridade": "Ensino Fundamental Completo",
                "aceitouTermos": true,
                "declarouMaioridade": true,
                "versaoTermos": "2026-09-25"
                }
                """;

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.mensagem").value(
                "E necessario ter pelo menos 18 anos para criar uma conta."
        ))
        .andExpect(jsonPath("$.momento").exists());
        }
}
