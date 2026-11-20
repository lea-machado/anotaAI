package br.com.anotaai.integration;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.GmailApiEmailService;
import br.com.anotaai.service.TermosService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaRotasIntegracaoTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    UsuarioRepository usuarios;
    @MockitoBean
    GmailApiEmailService emailService;
    @Autowired
    TermosService termosService;

    @Test
    void paginaPrivadaRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/pages/admin.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pages/login.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/anotacoes", "/api/flashcards", "/api/desempenho", "/api/auditoria", "/api/auth/me",
            "/api/admin/resumo" })
    void apisPrivadasExigemLogin(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isUnauthorized());
    }

    @Test
    void operacaoSemCsrfERecusada() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioAusenteInativoOuComPerfilInvalidoNaoAcessaDados() throws Exception {
        String email = "seguranca-" + UUID.randomUUID() + "@example.com";
        mvc.perform(get("/api/anotacoes").with(user(email))).andExpect(status().isForbidden());
        var u = new Usuario();
        u.setEmail(email);
        u.setNome("Teste");
        u.setPerfil("ADMIN");
        u.setAtivo(false);
        u = usuarios.save(u);
        try {
            mvc.perform(get("/api/anotacoes").with(user(email))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/resumo").with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
            u.setAtivo(true);
            u.setPerfil("INVALIDO");
            u = usuarios.save(u);
            mvc.perform(get("/api/anotacoes").with(user(email))).andExpect(status().isForbidden());
            u.setPerfil("ADMIN");
            u = usuarios.save(u);
            mvc.perform(get("/api/anotacoes").with(user(email).roles("ADMIN"))).andExpect(status().isForbidden());
            u.setVersaoTermos(termosService.atual().getVersao());
            u = usuarios.save(u);
            mvc.perform(get("/api/anotacoes").with(user(email).roles("ADMIN"))).andExpect(status().isOk());
        } finally {
            usuarios.delete(u);
        }
    }
}