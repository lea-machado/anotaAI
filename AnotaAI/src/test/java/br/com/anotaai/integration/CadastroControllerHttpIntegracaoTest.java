package br.com.anotaai.integration;

import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.GmailApiEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CadastroControllerHttpIntegracaoTest {

    private static final String BASE_PATH = "/api/auth/cadastro";

    @LocalServerPort
    private int port;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private GmailApiEmailService emailService;

    private RestClient client;

    @BeforeEach
    void configurarCliente() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {
                })
                .build();
    }

    private Map<String, Object> montarCadastro(String email, String nascimento) {
        return Map.of(
                "nome", "Usuario",
                "email", email,
                "senha", "senha1234",
                "dataNascimento", nascimento,
                "escolaridade", "Ensino Superior Incompleto",
                "aceitouTermos", true,
                "declarouMaioridade", true,
                "versaoTermos", "2026-09-25");
    }

    private ResponseEntity<Map<String, Object>> cadastrar(
            Map<String, Object> dados, String csrf, String cookie) {

        return client.post()
                .uri(BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-XSRF-TOKEN", csrf)
                .header("Cookie", cookie)
                .body(dados)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    private String[] obterCsrf() {

        ResponseEntity<Map<String, Object>> resposta = client.get()
                .uri("/api/auth/csrf")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        assertThat(resposta.getBody()).isNotNull();

        String token = (String) resposta.getBody().get("token");
        String cookie = resposta.getHeaders().getFirst("Set-Cookie");

        return new String[] {
                token,
                cookie == null ? "" : cookie.split(";", 2)[0]
        };
    }

    @Test
    void cadastrar_comDadosValidos_deveRetornar201ComCorpoEPersistir() {

        String email = "http-" + UUID.randomUUID() + "@teste.com";
        String[] csrf = obterCsrf();

        ResponseEntity<Map<String, Object>> resposta = cadastrar(montarCadastro(email, "2002-07-01"), csrf[0], csrf[1]);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(resposta.getBody())
                .containsEntry("email", email)
                .containsEntry("perfil", "USER")
                .containsEntry("mensagem", "Conta criada com sucesso.");

        assertThat(resposta.getBody().get("id")).isNotNull();

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void cadastrar_comMenorDeIdade_deveRetornar400ComMensagem() {

        String email = "menor-http-" + UUID.randomUUID() + "@teste.com";
        String[] csrf = obterCsrf();

        ResponseEntity<Map<String, Object>> resposta = cadastrar(montarCadastro(email, "2015-01-01"), csrf[0], csrf[1]);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(resposta.getBody())
                .containsEntry(
                        "mensagem",
                        "E necessario ter pelo menos 18 anos para criar uma conta.");

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void cadastrar_comEmailDuplicado_deveRetornar400ComMensagem() {

        String email = "duplicado-http-" + UUID.randomUUID() + "@teste.com";
        String[] csrf = obterCsrf();

        assertThat(cadastrar(
                montarCadastro(email, "2000-01-01"),
                csrf[0],
                csrf[1]).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> resposta = cadastrar(montarCadastro(email, "2002-07-01"), csrf[0], csrf[1]);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(resposta.getBody())
                .containsEntry(
                        "mensagem",
                        "Ja existe uma conta cadastrada com este e-mail.");

        assertThat(usuarioRepository.findByEmailIgnoreCase(email)).isPresent();
    }
}