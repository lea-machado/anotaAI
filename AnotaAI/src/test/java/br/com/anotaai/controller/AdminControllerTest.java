package br.com.anotaai.controller;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import br.com.anotaai.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final AnotacaoRepository notas = mock(AnotacaoRepository.class);
    final BaralhoRepository decks = mock(BaralhoRepository.class);
    final RegistroAuditoriaRepository registros = mock(RegistroAuditoriaRepository.class);
    final SolicitacaoExclusaoRepository pedidos = mock(SolicitacaoExclusaoRepository.class);
    final RegistroUsoIaRepository usos = mock(RegistroUsoIaRepository.class);
    final AuditoriaService auditoria = mock(AuditoriaService.class);
    final AdminService adminService = mock(AdminService.class);
    final UsuarioAtualService atual = mock(UsuarioAtualService.class);
    final GmailApiEmailService email = mock(GmailApiEmailService.class);
    final AdminController controller = new AdminController(usuarios, notas, decks, registros, pedidos, usos, auditoria,
            adminService, atual, email);
    final MockHttpServletRequest request = new MockHttpServletRequest();
    Usuario admin;

    @BeforeEach
    void preparar() {
        usuario(2L);
        admin = usuario(1L);
        admin.setPerfil("ADMIN");
        when(atual.obter()).thenReturn(admin);
    }

    Usuario usuario(Long id) {
        var u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(u, "idPublico", publico(id));
        when(usuarios.findById(id)).thenReturn(Optional.of(u));
        when(usuarios.findByIdPublico(u.getIdPublico())).thenReturn(Optional.of(u));
        u.setPerfil("USER");
        u.setNome("Pessoa");
        u.setEmail("pessoa" + id + "@example.com");
        return u;
    }

    String publico(Long id) {
        return UUID.nameUUIDFromBytes(("teste-conta-" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    SolicitacaoExclusao pedido() {
        var p = new SolicitacaoExclusao();
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 9L);
        p.setUsuarioId(2L);
        p.setNome("Pessoa");
        p.setEmail("pessoa@example.com");
        p.setSolicitadaEm(LocalDateTime.now());
        return p;
    }

    @Test
    void resumoFiltraPeriodoIncluindoLimites() {
        var dia = LocalDate.of(2026, 1, 10);
        var dentro = usuario(2L);
        dentro.setCriadoEm(dia.atStartOfDay());
        dentro.setAtivo(false);
        var antes = usuario(3L);
        antes.setCriadoEm(dia.minusDays(1).atStartOfDay());
        var depois = usuario(4L);
        depois.setCriadoEm(dia.plusDays(1).atStartOfDay());
        var semData = usuario(5L);
        semData.setCriadoEm(null);
        admin.setCriadoEm(dia.atTime(23, 59, 59));
        when(usuarios.findAll()).thenReturn(List.of(admin, dentro, antes, depois, semData));
        var nota = new Anotacao();
        org.springframework.test.util.ReflectionTestUtils.setField(nota, "criadoEm", dia.atStartOfDay());
        when(notas.findAll()).thenReturn(List.of(nota));
        var deck = new Baralho();
        org.springframework.test.util.ReflectionTestUtils.setField(deck, "criadoEm", dia.atStartOfDay());
        deck.getFlashcards().add(new Flashcard("P", "R"));
        var vazio = new Baralho();
        org.springframework.test.util.ReflectionTestUtils.setField(vazio, "flashcards", null);
        org.springframework.test.util.ReflectionTestUtils.setField(vazio, "criadoEm", dia.atStartOfDay());
        when(decks.findAll()).thenReturn(List.of(deck, vazio));
        when(registros.countByCriadoEmBetween(any())).thenReturn(8L);
        when(registros.countBySucessoFalseAndCriadoEmBetween(any())).thenReturn(2L);
        when(usos.countByCriadoEmBetween(any(), any())).thenReturn(3L);
        assertThat(controller.resumo(dia, dia)).containsEntry("usuarios", 2L).containsEntry("usuariosDesativados", 1L)
                .containsEntry("administradores", 1L).containsEntry("anotacoes", 1L).containsEntry("flashcards", 1L)
                .containsEntry("registrosAuditoria", 8L).containsEntry("falhasAuditoria", 2L)
                .containsEntry("usosIa", 3L);
        assertThat(controller.resumo(null, null)).containsEntry("usuarios", 5L);
        assertThat(controller.resumo(dia, null)).containsEntry("usuarios", 5L);
    }

    @Test
    void listagemOcultaEmailAnonimizadoEMarcaPedidoPendente() {
        var u = usuario(2L);
        u.setAnonimizado(true);
        when(usuarios.findAll()).thenReturn(List.of(u, admin));
        when(pedidos.findAll()).thenReturn(List.of(pedido()));
        assertThat(controller.usuarios().get(0)).containsEntry("email", "anonimizado").containsEntry("pedidoExclusao",
                true);
        assertThat(controller.usuarios().get(1)).containsEntry("email", admin.getEmail())
                .containsEntry("pedidoExclusao", false);
        var p = pedido();
        when(pedidos.findAllByOrderBySolicitadaEmAsc()).thenReturn(List.of(p));
        assertThat(controller.pedidosExclusao().get(0)).containsEntry("id", 9L).containsEntry("usuarioId", publico(2L))
                .containsEntry("solicitadaEm", p.getSolicitadaEm());
    }

    @Test
    void concluirExclusaoRemoveContaEReportaFalhaDeEmail() {
        var p = pedido();
        when(pedidos.findById(9L)).thenReturn(Optional.of(p));
        assertThat(controller.concluirExclusao(9L, request)).containsEntry("emailEnviado", true);
        var ordem = inOrder(adminService, email);
        ordem.verify(adminService).excluirConta(p, admin);
        ordem.verify(email).enviarConfirmacaoExclusao(p.getEmail(), p.getNome());
        doThrow(new ServicoEmailException("falhou")).when(email).enviarConfirmacaoExclusao(p.getEmail(), p.getNome());
        assertThat(controller.concluirExclusao(9L, request)).containsEntry("emailEnviado", false);
        assertThatThrownBy(() -> controller.concluirExclusao(10L, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void exclusaoDiretaExigePedidoDoTitular() {
        assertThatThrownBy(() -> controller.excluirUsuario(publico(2L), request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(adminService, email);
        var p = pedido();
        when(pedidos.findByUsuarioId(2L)).thenReturn(Optional.of(p));
        assertThat(controller.excluirUsuario(publico(2L), request)).containsEntry("emailEnviado", true).containsEntry("id", publico(2L));
        verify(adminService).excluirConta(p, admin);
        doThrow(new ServicoEmailException("falhou")).when(email).enviarConfirmacaoExclusao(p.getEmail(), p.getNome());
        assertThat(controller.excluirUsuario(publico(2L), request)).containsEntry("emailEnviado", false);
    }

    @Test
    void falhaAoExcluirContaNaoEnviaConfirmacao() {
        var p = pedido();
        when(pedidos.findById(9L)).thenReturn(Optional.of(p));
        doThrow(new IllegalStateException("Falha na exclusão")).when(adminService).excluirConta(p, admin);
        assertThatThrownBy(() -> controller.concluirExclusao(9L, request)).hasMessage("Falha na exclusão");
        verifyNoInteractions(email);
    }

    @Test
    void statusRespeitaPedidoPendente() {
        var u = usuario(2L);
        when(adminService.alternarAtivo(2L, admin)).thenReturn(u);
        assertThat(controller.alternarStatus(publico(2L), request)).containsEntry("ativo", true);
        u.setAtivo(false);
        assertThat(controller.alternarStatus(publico(2L), request)).containsEntry("ativo", false);
        when(pedidos.findByUsuarioId(2L)).thenReturn(Optional.of(pedido()));
        assertThatThrownBy(() -> controller.alternarStatus(publico(2L), request)).isInstanceOf(IllegalStateException.class);
        verify(adminService, times(2)).alternarAtivo(2L, admin);
    }

    @Test
    void alteracaoDePerfilRegistraSucessoEFalha() {
        var u = usuario(2L);
        when(adminService.alterarPerfil(2L, "USER", admin)).thenReturn(u);
        assertThat(controller.alterarPerfil(publico(2L), new AdminController.PerfilRequest("USER"), request))
                .containsEntry("perfil", "USER");
        when(adminService.alterarPerfil(2L, "USER", admin)).thenThrow(new IllegalArgumentException("bloqueado"));
        assertThatThrownBy(() -> controller.alterarPerfil(publico(2L), new AdminController.PerfilRequest("USER"), request))
                .hasMessage("bloqueado");
        verify(auditoria).registrar(eq(admin.getEmail()), eq("ALTERACAO_PERFIL_USUARIO"), anyString(), eq(false),
                eq("bloqueado"), eq(request));
    }

    @Test
    void auditoriaNaoRevelaIdentificacaoPessoal() {
        var anonimo = usuario(2L);
        anonimo.setAnonimizado(true);
        var lista = new ArrayList<RegistroAuditoria>();
        for (var u : Arrays.asList(anonimo, null, new Usuario(), admin)) {
            var r = new RegistroAuditoria();
            r.setUsuario(u);
            lista.add(r);
        }
        when(auditoria.listarTodos(null, null, null)).thenReturn(lista);
        var result = controller.auditoria(null, null, null);
        assertThat(result.get(0)).containsEntry("email", "Conta anonimizada").containsEntry("usuarioId", null);
        assertThat(result.get(1)).containsEntry("email", "Não identificado");
        assertThat(result.get(2)).containsEntry("email", "Não identificado");
        assertThat(result.get(3)).containsEntry("email", "pes***@example.com").containsEntry("enderecoIp", "-");
    }

    @Test
    void rotasAdministrativasUsamUuidERecusamNumeroSequencial() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var u = usuario(2L);
        when(adminService.alternarAtivo(2L, admin)).thenReturn(u);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/usuarios/" + u.getIdPublico() + "/status"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id")
                        .value(u.getIdPublico()));
        verify(adminService).alternarAtivo(2L, admin);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                "/api/admin/usuarios/2/status"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        verify(adminService, times(1)).alternarAtivo(anyLong(), any());
    }

    @Test
    void contasListadasExibemIdPublicoSemAlterarVinculosInternos() {
        var u = usuario(2L);
        when(usuarios.findAll()).thenReturn(List.of(u));
        when(pedidos.findAll()).thenReturn(List.of());
        assertThat(controller.usuarios().get(0)).containsEntry("id", u.getIdPublico());
        verify(notas).countByUsuarioId(2L);
    }

    @Test
    void auditoriaExibeIpEEmailMascaradoInclusiveEmEnderecosCurtos() {
        var conta = usuario(2L);
        conta.setEmail("joana@example.com");
        var registro = new RegistroAuditoria();
        registro.setUsuario(conta);
        registro.setEnderecoIp("2001:db8::1");
        when(auditoria.listarTodos(null, null, null)).thenReturn(List.of(registro));
        assertThat(controller.auditoria(null, null, null).get(0))
                .containsEntry("email", "joa***@example.com").containsEntry("enderecoIp", "2001:db8::1")
                .containsEntry("usuarioId", conta.getIdPublico());
        conta.setEmail("ab@example.com");
        assertThat(controller.auditoria(null, null, null).get(0)).containsEntry("email", "a***@example.com");
        conta.setAnonimizado(true);
        assertThat(controller.auditoria(null, null, null).get(0)).containsEntry("email", "Conta anonimizada");
    }
}
