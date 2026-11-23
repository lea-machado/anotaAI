package br.com.anotaai.controller;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CadastroControllerTest {
    UsuarioRepository repository = mock(UsuarioRepository.class);
    GmailApiEmailService email = mock(GmailApiEmailService.class);
    AuditoriaService auditoria = mock(AuditoriaService.class);
    AtomicReference<String> link = new AtomicReference<>();
    MockMvc mvc;
    String json = """
            {"nome":"Estudante","email":"estudante@example.com","senha":"Conta!Forte2026",
            "dataNascimento":"2000-01-01","escolaridade":"Ensino Superior Incompleto",
            "aceitouTermos":true,"declarouMaioridade":true,"versaoTermos":"2026-09-25"}
            """;

    @BeforeEach
    void preparar() throws Exception {
        var service = new VerificacaoCadastroService(new UsuarioService(repository, new BCryptPasswordEncoder(4)),
                email, "https://anotaai.example");
        mvc = MockMvcBuilders.standaloneSetup(new CadastroController(service, auditoria))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        doAnswer(i -> { link.set(i.getArgument(2)); return null; }).when(email)
                .enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
        when(repository.save(any())).thenAnswer(i -> {
            Usuario usuario = i.getArgument(0);
            var field = Usuario.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(usuario, 1L);
            return usuario;
        });
    }

    @Test
    void aceitaCadastroSemGravarEConfirmaSomenteComLinkDoEmail() throws Exception {
        var result = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.desafioId").isString())
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist()).andReturn();
        verify(repository, never()).save(any());
        verifyNoInteractions(auditoria);
        var id = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("desafioId").asText();
        mvc.perform(post("/api/auth/cadastro/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + id + "\"}")).andExpect(status().isBadRequest());
        var token = link.get().split("#token=", 2)[1];
        mvc.perform(post("/api/auth/cadastro/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.perfil").value("USER"));
        verify(repository).save(any());
        verify(auditoria).registrar(eq("estudante@example.com"), eq("CADASTRO_USUARIO"), anyString(),
                eq(true), anyString(), any());
        mvc.perform(post("/api/auth/cadastro/confirmar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void menorDeIdadeNaoRecebeEmailENaoESalvo() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("2000-01-01", "2015-01-01")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").value(
                        "E necessario ter pelo menos 18 anos para criar uma conta."));
        verifyNoInteractions(repository, email, auditoria);
    }

    @Test
    void escolaridadeObrigatoria() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("Ensino Superior Incompleto", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").value("Escolaridade é obrigatória."));
        verifyNoInteractions(repository, email, auditoria);
    }

    @Test
    void falhaNoEmailRetorna503SemSalvar() throws Exception {
        doThrow(new ServicoEmailException("Falha no envio", new RuntimeException())).when(email)
                .enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isServiceUnavailable());
        verify(repository, never()).save(any());
        verifyNoInteractions(auditoria);
    }
}
