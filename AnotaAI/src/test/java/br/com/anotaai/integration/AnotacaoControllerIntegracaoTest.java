
package br.com.anotaai.integration;

import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.repository.VersaoAnotacaoRepository;
import br.com.anotaai.service.GmailApiEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnotacaoControllerIntegracaoTest {

    private static final String BASE_PATH = "/api/anotacoes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnotacaoRepository anotacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VersaoAnotacaoRepository versaoAnotacaoRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GmailApiEmailService emailService;

    private String email;

    @BeforeEach
    void prepararUsuario() throws Exception {

        email = "anotacao-" + UUID.randomUUID() + "@teste.com";

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(email))
        ).andExpect(status().isCreated());
    }

    private String cadastroJson(String email) {
        return """
                {
                    "nome": "Usuario",
                    "email": "%s",
                    "senha": "senha1234",
                    "dataNascimento": "2002-05-02",
                    "escolaridade": "Ensino Superior Incompleto",
                    "aceitouTermos": true,
                    "declarouMaioridade": true,
                    "versaoTermos": "2026-09-25"
                }
                """.formatted(email);
    }

    private String anotacaoJson(String titulo, String conteudo) {
        return """
                {
                    "titulo": "%s",
                    "conteudo": "%s"
                }
                """.formatted(titulo, conteudo);
    }

    private long criarAnotacao(String titulo) throws Exception {

        var resposta = mockMvc.perform(
                post(BASE_PATH)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson(titulo, "Conteudo da aula"))
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(resposta.getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    @Test
    void criar_comDadosValidos_deveRetornar201EPersistir() throws Exception {

        String titulo = "Aula " + UUID.randomUUID();
        long id = criarAnotacao(titulo);

        var anotacao = anotacaoRepository.findById(id).orElseThrow();

        assertThat(anotacao.getTitulo()).isEqualTo(titulo);
        assertThat(anotacao.getConteudo()).isEqualTo("Conteudo da aula");

        Long usuarioId = usuarioRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow()
                .getId();

        assertThat(anotacao.getUsuario().getId()).isEqualTo(usuarioId);
    }

    @Test
    void listar_comAnotacaoCriada_deveRetornar200ComDados() throws Exception {

        long id = criarAnotacao("Anotação teste");

        mockMvc.perform(
                get(BASE_PATH)
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].titulo")
                        .value(org.hamcrest.Matchers.hasItem("Anotação teste")));
    }

    @Test
    void listar_comBusca_deveFiltrarPorTitulo() throws Exception {

        criarAnotacao("Minha anotação" + UUID.randomUUID());

        mockMvc.perform(
                get(BASE_PATH)
                        .param("busca", "Minha anotação")
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo")
                        .value(org.hamcrest.Matchers.startsWith("Minha anotação")));
    }

    @Test
    void buscar_comIdExistente_deveRetornar200ComCorpo() throws Exception {

        long id = criarAnotacao("Aula de Testes");

        mockMvc.perform(
                get(BASE_PATH + "/" + id)
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.titulo").value("Aula de Testes"))
                .andExpect(jsonPath("$.conteudo").value("Conteudo da aula"));
    }

    @Test
    void atualizar_comDadosValidos_deveRetornar200EPersistir() throws Exception {

        long id = criarAnotacao("Antes");

        mockMvc.perform(
                put(BASE_PATH + "/" + id)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson("Depois", "Conteudo atualizado"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Depois"))
                .andExpect(jsonPath("$.conteudo").value("Conteudo atualizado"));

        assertThat(anotacaoRepository.findById(id).orElseThrow().getTitulo())
                .isEqualTo("Depois");
    }

    @Test
    void versoes_comAnotacaoAtualizada_deveRetornar200ComHistorico() throws Exception {

        long id = criarAnotacao("Aula");

        mockMvc.perform(
                put(BASE_PATH + "/" + id)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson("Aula", "Novo conteudo"))
        ).andExpect(status().isOk());

        mockMvc.perform(
                get(BASE_PATH + "/" + id + "/versoes")
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ORIGINAL"))
                .andExpect(jsonPath("$[0].conteudo").value("Conteudo da aula"))
                .andExpect(jsonPath("$[1].conteudo").value("Novo conteudo"));

        assertThat(versaoAnotacaoRepository.findByAnotacaoIdOrderByCriadoEmAsc(id))
                .hasSize(2);
    }

    @Test
    void excluir_comIdExistente_deveRetornar204ERemover() throws Exception {

        configurarExclusaoEmCascataNoH2();

        long id = criarAnotacao("Para excluir");

        mockMvc.perform(
                delete(BASE_PATH + "/" + id)
                        .with(user(email).roles("USER"))
                        .with(csrf())
        ).andExpect(status().isNoContent());

        assertThat(anotacaoRepository.findById(id)).isEmpty();
    }

    private void configurarExclusaoEmCascataNoH2() throws Exception {

        try (
                var conexao = dataSource.getConnection();
                var chaves = conexao.getMetaData()
                        .getImportedKeys(null, null, "VERSOES_ANOTACAO")
        ) {

            while (chaves.next()) {

                if (!"ANOTACOES".equalsIgnoreCase(
                        chaves.getString("PKTABLE_NAME"))) {
                    continue;
                }

                if (chaves.getShort("DELETE_RULE")
                        == java.sql.DatabaseMetaData.importedKeyCascade) {
                    return;
                }

                String nome = chaves.getString("FK_NAME");

                assertThat(nome).isNotBlank();

                jdbcTemplate.execute(
                        "ALTER TABLE versoes_anotacao DROP CONSTRAINT \"" + nome + "\""
                );

                jdbcTemplate.execute(
                        "ALTER TABLE versoes_anotacao " +
                        "ADD CONSTRAINT fk_versoes_anotacao_teste " +
                        "FOREIGN KEY (anotacao_id) REFERENCES anotacoes(id) " +
                        "ON DELETE CASCADE"
                );

                return;
            }
        }

        throw new IllegalStateException(
                "Chave estrangeira de versoes_anotacao nao encontrada no H2."
        );
    }

    @Test
    void criar_semTitulo_deveRetornar400ComMensagem() throws Exception {

        mockMvc.perform(
                post(BASE_PATH)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson("", "Conteudo"))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value("Titulo e obrigatorio."));
    }

    @Test
    void criar_semConteudo_deveRetornar400ComMensagem() throws Exception {

        mockMvc.perform(
                post(BASE_PATH)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson("Titulo", ""))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem")
                        .value("Conteudo e obrigatorio."));
    }

    @Test
    void buscar_comIdInexistente_deveRetornar404ComMensagem() throws Exception {

        mockMvc.perform(
                get(BASE_PATH + "/999999999")
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Anotacao nao encontrada."));
    }

    @Test
    void atualizar_comIdInexistente_deveRetornar404ComMensagem() throws Exception {

        mockMvc.perform(
                put(BASE_PATH + "/999999999")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anotacaoJson("Titulo", "Conteudo"))
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Anotacao nao encontrada."));
    }

    @Test
    void excluir_comIdInexistente_deveRetornar404ComMensagem() throws Exception {

        mockMvc.perform(
                delete(BASE_PATH + "/999999999")
                        .with(user(email).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Anotacao nao encontrada."));
    }

    @Test
    void versoes_comIdInexistente_deveRetornar404ComMensagem() throws Exception {

        mockMvc.perform(
                get(BASE_PATH + "/999999999/versoes")
                        .with(user(email).roles("USER"))
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem")
                        .value("Anotacao nao encontrada."));
    }

    @Test
    void listar_comUsuarioDiferente_deveIsolarAnotacoes() throws Exception {

        criarAnotacao("Privada");

        String outro = "outro-" + UUID.randomUUID() + "@teste.com";

        mockMvc.perform(
                post("/api/auth/cadastro")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadastroJson(outro))
        ).andExpect(status().isCreated());

        mockMvc.perform(
                get(BASE_PATH)
                        .with(user(outro).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}