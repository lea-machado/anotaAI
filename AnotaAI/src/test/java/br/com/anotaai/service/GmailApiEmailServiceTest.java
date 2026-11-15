package br.com.anotaai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GmailApiEmailServiceTest {
    MockRestServiceServer server;
    GmailApiEmailService service;

    @BeforeEach
    void preparar() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GmailApiEmailService(builder, "client-test", "secret-test", "refresh-test", "sender@example.com");
    }

    void token(String body) {
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    void envio(String texto) {
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer token-test"))
                .andExpect(request -> {
                    var body = ((MockClientHttpRequest) request).getBodyAsString();
                    var raw = new ObjectMapper().readTree(body).get("raw").asText();
                    var mime = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                    assertThat(mime).contains("To: pessoa@example.com", texto,
                            "Content-Type: text/html; charset=UTF-8");
                }).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    }

    void enviar(int tipo) {
        switch (tipo) {
            case 0 -> service.enviarCodigo("pessoa@example.com", "Ana", "246810", 5);
            case 1 -> service.enviarCodigoRecuperacao("pessoa@example.com", "Ana", "246810", 5);
            case 2 -> service.enviarConfirmacaoExclusao("pessoa@example.com", null);
            default -> service.enviarAvisoPedidoExclusao("pessoa@example.com", null, "pessoa@example.com",
                    LocalDateTime.of(2026, 1, 1, 0, 0));
        }
    }

    @Test
    void enviaCodigoEscapandoHtmlEReutilizaToken() {
        token("{\"access_token\":\"token-test\",\"expires_in\":3600}");
        envio("&lt;Ana&gt;&amp;&quot;&#39;");
        envio("redefinir sua senha");
        service.enviarCodigo("pessoa@example.com", "<Ana>&\"'", "246810", 5);
        enviar(1);
        server.verify();
    }

    @Test
    void renovaTokenExpirado() {
        token("{\"access_token\":\"token-test\",\"expires_in\":0}");
        envio("246810");
        token("{\"access_token\":\"token-test\",\"expires_in\":3600}");
        envio("246810");
        enviar(0);
        enviar(0);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = { 2, 3 })
    void mensagensDeExclusaoComNomePadrao(int tipo) {
        token("{\"access_token\":\"token-test\",\"expires_in\":3600}");
        envio(tipo == 2 ? "estudante" : "2026-01-16");
        enviar(tipo);
        server.verify();
    }

    @Test
    void mensagensDeExclusaoComNomeInformado() {
        token("{\"access_token\":\"token-test\",\"expires_in\":3600}");
        envio("&lt;Ana&gt;");
        envio("&lt;Ana&gt;");
        service.enviarConfirmacaoExclusao("pessoa@example.com", "<Ana>");
        service.enviarAvisoPedidoExclusao("pessoa@example.com", "<Ana>", "pessoa@example.com", LocalDateTime.now());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3 })
    void falhaDeEnvioRetornaExcecaoDeDominio(int tipo) {
        token("{\"access_token\":\"token-test\",\"expires_in\":3600}");
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> enviar(tipo)).isInstanceOf(ServicoEmailException.class)
                .hasCauseInstanceOf(Exception.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 2, 3 })
    void falhaDeTokenPropagadaSemEnviarMensagem(int tipo) {
        token("{}");
        assertThatThrownBy(() -> enviar(tipo)).isInstanceOf(ServicoEmailException.class)
                .hasMessageContaining("token de acesso");
        server.verify();
    }

    @Test
    void respostaVaziaDeTokenERejeitada() {
        token("");
        assertThatThrownBy(() -> enviar(0)).isInstanceOf(ServicoEmailException.class)
                .hasMessageContaining("token de acesso");
        server.verify();
    }

    @Test
    void erroOAuthETraduzido() {
        server.expect(requestTo("https://oauth2.googleapis.com/token")).andRespond(withUnauthorizedRequest());
        assertThatThrownBy(() -> enviar(0)).isInstanceOf(ServicoEmailException.class)
                .hasMessageContaining("autenticar");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3 })
    void configuracaoIncompletaNaoFazRequisicao(int indice) {
        String[] dados = { "client", "secret", "refresh", "sender@example.com" };
        dados[indice] = " ";
        service = new GmailApiEmailService(RestClient.builder(), dados[0], dados[1], dados[2], dados[3]);
        assertThatThrownBy(() -> enviar(0)).isInstanceOf(ServicoEmailException.class)
                .hasMessageContaining("nao configurada");
        server.verify();
    }
}