package br.com.anotaai.controller;

import br.com.anotaai.model.*;
import br.com.anotaai.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RotasComplementaresTest {
        final ObjectMapper mapper = new ObjectMapper();
        final OpenRouterService ia = mock(OpenRouterService.class);
        final RegistroUsoIaService uso = mock(RegistroUsoIaService.class);
        final AnotacaoService notas = mock(AnotacaoService.class);
        final FlashcardService cards = mock(FlashcardService.class);
        final RecuperacaoSenhaService recuperacao = mock(RecuperacaoSenhaService.class);
        final DesempenhoService desempenho = mock(DesempenhoService.class);
        final AuditoriaService auditoria = mock(AuditoriaService.class);
        final UsuarioAtualService atual = mock(UsuarioAtualService.class);
        final GeracaoController geracao = new GeracaoController(ia, uso, notas, atual);
        MockMvc mvc;

        @BeforeEach
        void preparar() {
                mvc = MockMvcBuilders.standaloneSetup(geracao, new FlashcardController(cards),
                                new RecuperacaoSenhaController(recuperacao), new DesempenhoController(desempenho),
                                new AuditoriaController(auditoria, atual))
                                .setControllerAdvice(new ApiExceptionHandler()).build();
        }

        @Test
        void gerarUsaAnotacaoAutorizadaERegistraUso() throws Exception {
                var nota = new Anotacao();
                nota.setTitulo("Aula");
                nota.setConteudo("Original");
                when(notas.buscar(1L)).thenReturn(nota);
                var usuario = new Usuario();
                usuario.setEscolaridade("Ensino Médio Completo");
                when(atual.obter()).thenReturn(usuario);
                when(ia.gerar("Título: Aula\n\nConteúdo:\nOriginal", "resumo", null, "Ensino Médio Completo"))
                                .thenReturn(mapper.readTree("{\"resumo\":\"Resultado\"}"));
                mvc.perform(post("/api/anotacoes/gerar/resumo").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"anotacaoId\":1}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.resumo").value("Resultado"));
                verify(uso).registrar("resumo");
                geracao.gerar("resumo", new GeracaoController.Pedido(1L, null, "Alternativo"));
                verify(ia).gerar("Título: Aula\n\nConteúdo:\nAlternativo", "resumo", null, "Ensino Médio Completo");
                when(notas.buscarComResumoAprovado(1L)).thenReturn(nota);
                geracao.gerar("flashcards", new GeracaoController.Pedido(1L, 5, null));
                verify(notas).buscarComResumoAprovado(1L);
                verify(ia).gerar("Título: Aula\n\nConteúdo:\nOriginal", "flashcards", 5, "Ensino Médio Completo");
                assertThatThrownBy(
                                () -> geracao.gerar("flashcards", new GeracaoController.Pedido(1L, 5, "Alternativo")))
                                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void flashcardsSemResumoAprovadoNaoChamamIa() throws Exception {
                when(notas.buscarComResumoAprovado(1L))
                                .thenThrow(new IllegalStateException("Aprove o resumo primeiro."));
                mvc.perform(post("/api/anotacoes/gerar/flashcards").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"anotacaoId\":1,\"quantidade\":5}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.mensagem").value("Aprove o resumo primeiro."));
                verifyNoInteractions(ia, uso);
        }

        @Test
        void verificarInconsistenciasNaoModificaAnotacao() throws Exception {
                var nota = new Anotacao();
                nota.setTitulo("Aula");
                nota.setConteudo("Original");
                when(notas.buscar(1L)).thenReturn(nota);
                var usuario = new Usuario();
                usuario.setEscolaridade("Ensino Superior Incompleto");
                when(atual.obter()).thenReturn(usuario);
                when(ia.verificarInconsistencias("Aula", "Original", "Ensino Superior Incompleto"))
                                .thenReturn(mapper.readTree("{\"sugestoes\":[],\"fontes\":[]}"));
                mvc.perform(post("/api/anotacoes/gerar/inconsistencias").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"anotacaoId\":1}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.sugestoes").isEmpty());
                verify(uso).registrar("inconsistencias");
                assertThat(nota.getConteudo()).isEqualTo("Original");
        }

        @Test
        void geracaoExigeNotaSalva() throws Exception {
                for (var tipo : List.of("resumo", "inconsistencias")) {
                        mvc.perform(post("/api/anotacoes/gerar/" + tipo).contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.mensagem").isString());
                }
                assertThatThrownBy(() -> geracao.gerar("resumo", null)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> geracao.verificarInconsistencias(null))
                                .isInstanceOf(IllegalArgumentException.class);
                verifyNoInteractions(ia, uso, notas);
        }

        static Stream<org.junit.jupiter.params.provider.Arguments> erros() {
                return Stream.of(
                                org.junit.jupiter.params.provider.Arguments.of(new IllegalArgumentException("inválido"),
                                                400),
                                org.junit.jupiter.params.provider.Arguments.of(new IllegalStateException("conflito"),
                                                409),
                                org.junit.jupiter.params.provider.Arguments
                                                .of(new RecursoNaoEncontradoException("ausente"), 404),
                                org.junit.jupiter.params.provider.Arguments.of(new OpenRouterException("indisponível"),
                                                503),
                                org.junit.jupiter.params.provider.Arguments.of(new ServicoEmailException("email"), 503),
                                org.junit.jupiter.params.provider.Arguments.of(
                                                new org.springframework.dao.DuplicateKeyException("duplicado"), 409),
                                org.junit.jupiter.params.provider.Arguments
                                                .of(new org.springframework.dao.OptimisticLockingFailureException(
                                                                "concorrência"), 409));
        }

        @Test
        void cotaDeTokensRetorna429ComPrazoDeRenovacao() throws Exception {
                when(cards.buscar(9L)).thenThrow(new LimiteTokensIaException(java.time.Instant.now().plusSeconds(120)));
                mvc.perform(get("/api/flashcards/9"))
                        .andExpect(status().isTooManyRequests())
                        .andExpect(header().exists("Retry-After"))
                        .andExpect(jsonPath("$.codigo").value("LIMITE_TOKENS_IA"))
                        .andExpect(jsonPath("$.reiniciaEm").isString())
                        .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Nova cota em")));
        }

        @ParameterizedTest
        @MethodSource("erros")
        void excecoesTemStatusECorpoConsistentes(RuntimeException erro, int status) throws Exception {
                when(cards.buscar(9L)).thenThrow(erro);
                mvc.perform(get("/api/flashcards/9")).andExpect(status().is(status))
                                .andExpect(jsonPath("$.mensagem").isString()).andExpect(jsonPath("$.momento").exists());
        }

        @Test
        void flashcardsExponemOperacoesEFiltros() throws Exception {
                var detalhe = new FlashcardService.Detalhe(8L, "Aula", 1L, LocalDateTime.now(),
                                List.of(new FlashcardService.CartaoEstudo(0, "P", "R", 1, 0, List.of("A", "B", "C"))));
                when(cards.salvar(any())).thenReturn(detalhe);
                when(cards.buscar(8L)).thenReturn(detalhe);
                when(cards.registrarResultado(8L, 0, true)).thenReturn(detalhe);
                mvc.perform(post("/api/flashcards").contentType(MediaType.APPLICATION_JSON).content(
                                "{\"titulo\":\"Aula\",\"anotacaoId\":1,\"flashcards\":[{\"pergunta\":\"P\",\"resposta\":\"R\"}]}"))
                                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(8));
                mvc.perform(get("/api/flashcards/8")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.flashcards[0].pergunta").value("P"))
                                .andExpect(jsonPath("$.flashcards[0].distratores[0]").value("A"));
                mvc.perform(get("/api/flashcards").param("anotacaoId", "1")).andExpect(status().isOk());
                verify(cards).listar(1L);
                mvc.perform(get("/api/flashcards/revisao-erros").param("anotacaoId", "1").param("baralhoId", "8"))
                                .andExpect(status().isOk());
                verify(cards).revisarErros(1L, 8L);
                mvc.perform(post("/api/flashcards/8/cartoes/0/resultado").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"correto\":true}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.flashcards[0].acertos").value(1));
                mvc.perform(post("/api/flashcards/8/cartoes/0/resultado").contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
                assertThatThrownBy(() -> new FlashcardController(cards).registrarResultado(8L, 0, null))
                                .isInstanceOf(IllegalArgumentException.class);
                mvc.perform(delete("/api/flashcards/8")).andExpect(status().isNoContent());
                verify(cards).excluir(8L);
        }

        @Test
        void recuperacaoRecebeDesafioERedefineSenha() throws Exception {
                var id = UUID.randomUUID();
                when(recuperacao.solicitar("pessoa@example.com")).thenReturn(id);
                mvc.perform(post("/api/auth/recuperacao/solicitar").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"pessoa@example.com\"}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.desafioId").value(id.toString()));
                mvc.perform(post("/api/auth/recuperacao/redefinir").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"desafioId\":\"" + id
                                                + "\",\"codigo\":\"246810\",\"novaSenha\":\"Conta!Forte2026\"}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.mensagem").isString());
                verify(recuperacao).redefinir(id, "246810", "Conta!Forte2026");
        }

        @Test
        void desempenhoEAuditoriaRetornamDadosDoUsuario() throws Exception {
                when(desempenho.obter()).thenReturn(Map.of("anotacoes", 2));
                mvc.perform(get("/api/desempenho")).andExpect(status().isOk())
                                .andExpect(jsonPath("$.anotacoes").value(2));
                var u = new Usuario();
                org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 7L);
                when(atual.obter()).thenReturn(u);
                var registro = new RegistroAuditoria();
                org.springframework.test.util.ReflectionTestUtils.setField(registro, "id", 1L);
                registro.setAcao("LOGIN");
                registro.setRecurso("/api");
                registro.setCriadoEm(java.time.Instant.now());
                when(auditoria.listarDoUsuario(7L)).thenReturn(List.of(registro));
                mvc.perform(get("/api/auditoria")).andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].acao").value("LOGIN"))
                                .andExpect(jsonPath("$[0].email").doesNotExist());
        }
}
