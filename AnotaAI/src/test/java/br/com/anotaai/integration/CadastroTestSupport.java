package br.com.anotaai.integration;

import br.com.anotaai.service.GmailApiEmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class CadastroTestSupport {
    private CadastroTestSupport() { }

    static ResultActions cadastrarConfirmado(MockMvc mvc, GmailApiEmailService emailService, String json) throws Exception {
        var link = new AtomicReference<String>();
        doAnswer(i -> { link.set(i.getArgument(2)); return null; }).when(emailService)
                .enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isAccepted());
        var token = link.get().split("#token=", 2)[1];
        return mvc.perform(post("/api/auth/cadastro/confirmar").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("token", token))));
    }
}
