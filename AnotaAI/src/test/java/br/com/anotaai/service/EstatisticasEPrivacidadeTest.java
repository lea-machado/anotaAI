package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EstatisticasEPrivacidadeTest {
    private Usuario usuario() {
        var u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 7L);
        u.setNome("Pessoa");
        u.setEmail("pessoa@example.com");
        return u;
    }

    @Test
    void desempenhoVazioNaoDividePorZero() {
        var atual = mock(UsuarioAtualService.class);
        when(atual.obter()).thenReturn(usuario());
        var service = new DesempenhoService(mock(AnotacaoRepository.class), atual, mock(BaralhoRepository.class));
        assertThat(service.obter()).containsEntry("anotacoes", 0).containsEntry("mediaCaracteres", 0)
                .containsEntry("ultimaAtualizacao", "").containsEntry("taxaAcertos", 0L).containsEntry("taxaErros", 0L);
    }

    @Test
    void desempenhoAgregaSomenteDadosDoUsuario() {
        var atual = mock(UsuarioAtualService.class);
        when(atual.obter()).thenReturn(usuario());
        var notas = mock(AnotacaoRepository.class);
        var decks = mock(BaralhoRepository.class);
        var a = new Anotacao();
        a.setConteudo("abc");
        org.springframework.test.util.ReflectionTestUtils.setField(a, "atualizadoEm",
                LocalDateTime.of(2026, 1, 1, 0, 0));
        var b = new Anotacao();
        b.setConteudo("abcde");
        org.springframework.test.util.ReflectionTestUtils.setField(b, "atualizadoEm", a.getAtualizadoEm().plusDays(1));
        var deck = new Baralho();
        var card = new Flashcard("P", "R");
        card.registrarResultado(true);
        card.registrarResultado(true);
        card.registrarResultado(false);
        deck.getFlashcards().add(card);
        when(notas.findByUsuarioIdOrderByAtualizadoEmDesc(7L)).thenReturn(List.of(a, b));
        when(decks.findByUsuarioIdOrderByCriadoEmDesc(7L)).thenReturn(List.of(deck));
        assertThat(new DesempenhoService(notas, atual, decks).obter())
                .containsEntry("anotacoes", 2).containsEntry("caracteres", 8).containsEntry("mediaCaracteres", 4)
                .containsEntry("ultimaAtualizacao", b.getAtualizadoEm()).containsEntry("flashcards", 1)
                .containsEntry("acertos", 2).containsEntry("erros", 1)
                .containsEntry("taxaAcertos", 67L).containsEntry("taxaErros", 33L);
    }

    @Test
    void solicitarExclusaoPreservaPedidoESuspendeAcesso() {
        var pedidos = mock(SolicitacaoExclusaoRepository.class);
        var usuarios = mock(UsuarioRepository.class);
        var auditoria = mock(AuditoriaService.class);
        var u = usuario();
        var result = new SolicitacaoExclusaoService(pedidos, usuarios, auditoria).solicitar(u, null);
        assertThat(result.getUsuarioId()).isEqualTo(7L);
        assertThat(result.getNome()).isEqualTo(u.getNome());
        assertThat(result.getEmail()).isEqualTo(u.getEmail());
        assertThat(result.getSolicitadaEm()).isNotNull();
        assertThat(u.isAtivo()).isFalse();
        verify(pedidos).save(result);
        verify(usuarios).save(u);
        verify(auditoria).registrar(eq(u.getEmail()), eq("SOLICITACAO_EXCLUSAO_CONTA"), anyString(), eq(true),
                anyString(), isNull());
    }

    @Test
    void pedidoDuplicadoNaoAlteraConta() {
        var pedidos = mock(SolicitacaoExclusaoRepository.class);
        var usuarios = mock(UsuarioRepository.class);
        var auditoria = mock(AuditoriaService.class);
        when(pedidos.findByUsuarioId(7L)).thenReturn(Optional.of(new SolicitacaoExclusao()));
        assertThatThrownBy(
                () -> new SolicitacaoExclusaoService(pedidos, usuarios, auditoria).solicitar(usuario(), null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(usuarios, auditoria);
        verify(pedidos, never()).save(any());
    }

    @Test
    void usoDeIaRegistraUsuarioFuncionalidadeEData() {
        var repository = mock(RegistroUsoIaRepository.class);
        var atual = mock(UsuarioAtualService.class);
        when(atual.obter()).thenReturn(usuario());
        new RegistroUsoIaService(repository, atual).registrar("resumo");
        var captor = ArgumentCaptor.forClass(RegistroUsoIa.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(7L);
        assertThat(captor.getValue().getFuncionalidade()).isEqualTo("resumo");
        assertThat(captor.getValue().getCriadoEm()).isNotNull();
    }

    @Test
    void auditoriaLimitaTextoEAceitaEventosSemUsuario() {
        var repo = mock(RegistroAuditoriaRepository.class);
        var usuarios = mock(UsuarioRepository.class);
        var service = new AuditoriaService(repo, usuarios);
        var u = usuario();
        when(usuarios.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        service.registrar(u.getEmail(), "A".repeat(70), "R".repeat(300), false, "privado", null);
        service.registrar(null, null, null, true, null, null);
        service.registrar(" ", "LOGIN", "/api", true, null, null);
        var captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(repo, times(3)).save(captor.capture());
        var registros = captor.getAllValues();
        assertThat(registros.get(0).getUsuario()).isSameAs(u);
        assertThat(registros.get(0).getAcao()).hasSize(60);
        assertThat(registros.get(0).getRecurso()).hasSize(255);
        assertThat(registros.get(0).isSucesso()).isFalse();
        assertThat(registros.get(0).getCriadoEm()).isNotNull();
        assertThat(registros.get(1).getUsuario()).isNull();
        assertThat(registros.get(1).getAcao()).isNull();
        assertThat(registros.get(2).getAcao()).isEqualTo("LOGIN");
        when(repo.findTop50ByUsuarioIdOrderByCriadoEmDesc(7L)).thenReturn(List.of(registros.get(0)));
        when(repo.findTop100ByOrderByCriadoEmDesc()).thenReturn(registros);
        assertThat(service.listarDoUsuario(7L)).containsExactly(registros.get(0));
        assertThat(service.listarTodos(null, null, null)).containsExactlyElementsOf(registros);
    }

    @Test
    void auditoriaFiltraPeriodoEResultadoAntesDoLimite() {
        var repo = mock(RegistroAuditoriaRepository.class);
        var service = new AuditoriaService(repo, mock(UsuarioRepository.class));
        var dia = java.time.LocalDate.of(2026, 11, 15);
        var periodo = org.springframework.data.domain.Range.rightOpen(dia.atStartOfDay(),
                dia.plusDays(1).atStartOfDay());
        var falha = new RegistroAuditoria();
        when(repo.findTop100BySucessoAndCriadoEmBetweenOrderByCriadoEmDesc(false, periodo)).thenReturn(List.of(falha));

        assertThat(service.listarTodos(dia, dia, false)).containsExactly(falha);
        verify(repo).findTop100BySucessoAndCriadoEmBetweenOrderByCriadoEmDesc(false, periodo);
        verifyNoMoreInteractions(repo);
    }
}
