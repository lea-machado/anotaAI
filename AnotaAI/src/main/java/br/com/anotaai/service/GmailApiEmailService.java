package br.com.anotaai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class GmailApiEmailService {
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String remetente;
    private String accessToken;
    private Instant accessTokenExpiraEm = Instant.EPOCH;

    public GmailApiEmailService(RestClient.Builder restClientBuilder,
                                @Value("${gmail.api.client-id:}") String clientId,
                                @Value("${gmail.api.client-secret:}") String clientSecret,
                                @Value("${gmail.api.refresh-token:}") String refreshToken,
                                @Value("${gmail.api.sender-email:}") String remetente) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.remetente = remetente;
    }

    public void enviarCodigo(String destinatario, String nome, String codigo, int minutosValidade) {
        enviarCodigo(destinatario, nome, codigo, minutosValidade, false);
    }

    public void enviarCodigoRecuperacao(String destinatario, String nome, String codigo, int minutosValidade) {
        enviarCodigo(destinatario, nome, codigo, minutosValidade, true);
    }

    private void enviarCodigo(String destinatario, String nome, String codigo, int minutosValidade, boolean recuperacao) {
        validarConfiguracao();
        try {
            var mensagem = montarMensagem(destinatario, nome, codigo, minutosValidade);
            if (recuperacao) {
                mensagem = mensagem.replace("Codigo de verificacao", "Recuperacao de senha")
                        .replace("concluir seu acesso", "redefinir sua senha")
                        .replace("Se voce nao tentou entrar, ignore este e-mail.",
                                "Se voce nao solicitou a troca de senha, ignore este e-mail. Sua senha permanece igual.");
            }
            var raw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mensagem.getBytes(StandardCharsets.UTF_8));

            restClient.post()
                    .uri("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                    .headers(headers -> headers.setBearerAuth(obterAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", raw))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ServicoEmailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServicoEmailException("Nao foi possivel enviar o codigo por e-mail.", exception);
        }
    }

    private synchronized String obterAccessToken() {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiraEm.minusSeconds(60))) {
            return accessToken;
        }

        var formulario = new LinkedMultiValueMap<String, String>();
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("refresh_token", refreshToken);
        formulario.add("grant_type", "refresh_token");

        try {
            var resposta = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(TokenResponse.class);
            if (resposta == null || resposta.access_token() == null) {
                throw new ServicoEmailException("A Gmail API nao retornou um token de acesso.");
            }
            accessToken = resposta.access_token();
            accessTokenExpiraEm = Instant.now().plusSeconds(resposta.expires_in());
            return accessToken;
        } catch (ServicoEmailException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServicoEmailException("Nao foi possivel autenticar na Gmail API.", exception);
        }
    }

    private String montarMensagem(String destinatario, String nome, String codigo, int minutosValidade) {
        var html = """
                <html><body style="font-family:Arial,sans-serif;color:#122838">
                <h2>Codigo de verificacao do AnotaAI</h2>
                <p>Ola, %s.</p>
                <p>Use o codigo abaixo para concluir seu acesso:</p>
                <p style="font-size:32px;font-weight:bold;letter-spacing:8px">%s</p>
                <p>O codigo expira em %d minutos e so pode ser usado uma vez.</p>
                <p>Se voce nao tentou entrar, ignore este e-mail.</p>
                </body></html>
                """.formatted(escaparHtml(nome), codigo, minutosValidade);

        return "From: AnotaAI <%s>\r\n".formatted(remetente)
                + "To: %s\r\n".formatted(destinatario)
                + "Subject: Codigo de verificacao - AnotaAI\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n\r\n"
                + html;
    }

    private String escaparHtml(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void validarConfiguracao() {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank() || remetente.isBlank()) {
            throw new ServicoEmailException(
                    "Gmail API nao configurada. Defina GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN e GMAIL_SENDER_EMAIL.");
        }
    }

    private record TokenResponse(String access_token, long expires_in) {}
}
