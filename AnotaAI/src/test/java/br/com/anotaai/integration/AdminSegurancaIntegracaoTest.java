package br.com.anotaai.integration;

import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.GmailApiEmailService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSegurancaIntegracaoTest {
        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private UsuarioRepository usuarioRepository;
        @MockitoBean
        private GmailApiEmailService emailService;

        private String emailUsuario;
        private String emailAdmin;
        private final AtomicReference<String> codigoEnviado = new AtomicReference<>();

        @BeforeEach
        void prepararUsuarios() throws Exception {
                emailUsuario = "usuario-" + UUID.randomUUID() + "@teste.com";
                emailAdmin = "admin-" + UUID.randomUUID() + "@teste.com";
                cadastrar(emailUsuario);
                cadastrar(emailAdmin);
                var administrador = usuarioRepository.findByEmailIgnoreCase(emailAdmin).orElseThrow();
                administrador.setPerfil("ADMIN");
                usuarioRepository.save(administrador);
                Mockito.doAnswer(invocacao -> {
                        codigoEnviado.set(invocacao.getArgument(2));
                        return null;
                }).when(emailService).enviarCodigo(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                                Mockito.anyInt());
        }

        private void cadastrar(String email) throws Exception {
                String dados = """
                                {"nome":"Usuario","email":"%s","senha":"senha1234",
                                "dataNascimento":"2000-09-10","escolaridade":"Ensino Superior Incompleto",
                                "aceitouTermos":true,"declarouMaioridade":true,"versaoTermos":"2026-09-25"}
                                """.formatted(email);
                mockMvc.perform(post("/api/auth/cadastro").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(dados))
                                .andExpect(status().isCreated());
        }

        private HttpSession autenticar(String email) throws Exception {
                String dados = """
                                {"email":"%s","senha":"senha1234"}
                                """.formatted(email);
                var login = mockMvc.perform(post("/api/auth/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(dados))
                                .andExpect(status().isAccepted()).andExpect(jsonPath("$.desafioId").exists())
                                .andReturn();
                String desafioId = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(login.getResponse().getContentAsString()).get("desafioId").asText();
                String verificacao = """
                                {"desafioId":"%s","codigo":"%s"}
                                """.formatted(desafioId, codigoEnviado.get());
                var autenticacao = mockMvc.perform(post("/api/auth/2fa/verify").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON).content(verificacao))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.mensagem").value("Login realizado com sucesso."))
                                .andReturn();
                return autenticacao.getRequest().getSession(false);
        }

        @Test
        void resumo_semAutenticacao_deveRetornar401() throws Exception {
                mockMvc.perform(get("/api/admin/resumo"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void resumo_comUsuarioSemPermissao_deveRetornar403() throws Exception {
                HttpSession sessao = autenticar(emailUsuario);
                mockMvc.perform(get("/api/admin/resumo").session((org.springframework.mock.web.MockHttpSession) sessao))
                                .andExpect(status().isForbidden());
        }

        @Test
        void resumo_comAdministrador_deveRetornar200ComCorpo() throws Exception {
                HttpSession sessao = autenticar(emailAdmin);
                mockMvc.perform(get("/api/admin/resumo").session((org.springframework.mock.web.MockHttpSession) sessao))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.usuarios").isNumber())
                                .andExpect(jsonPath("$.anotacoes").isNumber())
                                .andExpect(jsonPath("$.registrosAuditoria").isNumber());
        }
}