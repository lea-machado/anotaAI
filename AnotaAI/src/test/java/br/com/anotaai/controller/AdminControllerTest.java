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
        admin = usuario(1L);
        admin.setPerfil("ADMIN");
        when(atual.obter()).thenReturn(admin);
    }

    Usuario usuario(Long id) {
        var u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        u.setPerfil("USER");
        u.setNome("Pessoa");
        u.setEmail("pessoa" + id + "@example.com");
        return u;
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
        when(registros.countByCriadoEmBetween(any(), any())).thenReturn(8L);
        when(registros.countBySucessoFalseAndCriadoEmBetween(any(), any())).thenReturn(2L);
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
        assertThat(controller.pedidosExclusao().get(0)).containsEntry("id", 9L).containsEntry("usuarioId", 2L)
                .containsEntry("solicitadaEm", p.getSolicitadaEm());
    }

    @Test
    void concluirExclusaoAnonimizaRemovePedidoEReportaFalhaDeEmail() {
        var p = pedido();
        when(pedidos.findById(9L)).thenReturn(Optional.of(p));
        assertThat(controller.concluirExclusao(9L, request)).containsEntry("emailEnviado", true);
        verify(adminService).anonimizar(2L, admin);
        verify(pedidos).delete(p);
        doThrow(new ServicoEmailException("falhou")).when(email).enviarConfirmacaoExclusao(p.getEmail(), p.getNome());
        assertThat(controller.concluirExclusao(9L, request)).containsEntry("emailEnviado", false);
        assertThatThrownBy(() -> controller.concluirExclusao(10L, request))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void exclusaoDiretaExigePedidoDoTitular() {
        assertThatThrownBy(() -> controller.excluirUsuario(2L, request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(adminService, email);
        var p = pedido();
        when(pedidos.findByUsuarioId(2L)).thenReturn(Optional.of(p));
        assertThat(controller.excluirUsuario(2L, request)).containsEntry("emailEnviado", true).containsEntry("id", 2L);
        doThrow(new ServicoEmailException("falhou")).when(email).enviarConfirmacaoExclusao(p.getEmail(), p.getNome());
        assertThat(controller.excluirUsuario(2L, request)).containsEntry("emailEnviado", false);
    }

    @Test
    void statusRespeitaPedidoPendente() {
        var u = usuario(2L);
        when(adminService.alternarAtivo(2L, admin)).thenReturn(u);
        assertThat(controller.alternarStatus(2L, request)).containsEntry("ativo", true);
        u.setAtivo(false);
        assertThat(controller.alternarStatus(2L, request)).containsEntry("ativo", false);
        when(pedidos.findByUsuarioId(2L)).thenReturn(Optional.of(pedido()));
        assertThatThrownBy(() -> controller.alternarStatus(2L, request)).isInstanceOf(IllegalStateException.class);
        verify(adminService, times(2)).alternarAtivo(2L, admin);
    }

    @Test
    void alteracaoDePerfilRegistraSucessoEFalha() {
        var u = usuario(2L);
        when(adminService.alterarPerfil(2L, "USER", admin)).thenReturn(u);
        assertThat(controller.alterarPerfil(2L, new AdminController.PerfilRequest("USER"), request))
                .containsEntry("perfil", "USER");
        when(adminService.alterarPerfil(2L, "USER", admin)).thenThrow(new IllegalArgumentException("bloqueado"));
        assertThatThrownBy(() -> controller.alterarPerfil(2L, new AdminController.PerfilRequest("USER"), request))
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
        when(auditoria.listarTodos()).thenReturn(lista);
        var result = controller.auditoria();
        assertThat(result.get(0)).containsEntry("email", "Conta anonimizada");
        assertThat(result.get(1)).containsEntry("email", "Não identificado");
        assertThat(result.get(2)).containsEntry("email", "Não identificado");
        assertThat(result.get(3)).containsEntry("email", "Conta 1").containsEntry("enderecoIp", "-");
    }
}