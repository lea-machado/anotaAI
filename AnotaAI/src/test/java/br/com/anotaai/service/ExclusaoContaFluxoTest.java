package br.com.anotaai.service;

import br.com.anotaai.model.SolicitacaoExclusao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExclusaoContaFluxoTest {
    private final SolicitacaoExclusaoRepository pedidos = mock(SolicitacaoExclusaoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SolicitacaoExclusaoService service = new SolicitacaoExclusaoService(pedidos, usuarios, auditoria);

    @Test
    void recuperaContaAntesDoPrazoSemApagarDados() {
        var usuario = usuario(false);
        var pedido = pedido(LocalDateTime.now().minusDays(3));
        when(pedidos.findByUsuarioId(9L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario));

        var recuperado = service.recuperar(9L, null);

        assertThat(recuperado.isAtivo()).isTrue();
        verify(usuarios).save(usuario);
        verify(pedidos).delete(pedido);
        verify(usuarios, never()).delete(any());
        verify(auditoria).registrar(eq("aluno@teste.com"), eq("CANCELAMENTO_EXCLUSAO_CONTA"),
                anyString(), eq(true), anyString(), isNull());
    }

    @Test
    void naoRecuperaContaAposQuinzeDias() {
        when(pedidos.findByUsuarioId(9L)).thenReturn(Optional.of(pedido(LocalDateTime.now().minusDays(16))));
        assertThatThrownBy(() -> service.recuperar(9L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prazo");
        verifyNoInteractions(usuarios);
        verify(pedidos, never()).delete(any());
    }

    @Test
    void naoRecuperaContaSemPedidoOuJaAtiva() {
        assertThatThrownBy(() -> service.recuperar(9L, null)).isInstanceOf(IllegalStateException.class);
        var pedido = pedido(LocalDateTime.now().minusDays(1));
        when(pedidos.findByUsuarioId(9L)).thenReturn(Optional.of(pedido));
        when(usuarios.findById(9L)).thenReturn(Optional.of(usuario(true)));
        assertThatThrownBy(() -> service.recuperar(9L, null)).hasMessageContaining("não está aguardando");
        verify(pedidos, never()).delete(any());
    }

    @Test
    void agendaExclusaoApenasDePedidosVencidosEContinuaSeOutroFalhar() {
        var vencido = pedido(LocalDateTime.now().minusDays(16));
        var outro = pedido(LocalDateTime.now().minusDays(20));
        ReflectionTestUtils.setField(vencido, "id", 10L);
        ReflectionTestUtils.setField(outro, "id", 11L);
        outro.setUsuarioId(10L);
        outro.setEmail("outro@teste.com");
        outro.setNome("Outro aluno");
        var admin = mock(AdminService.class);
        var email = mock(GmailApiEmailService.class);
        when(pedidos.findBySolicitadaEmLessThanEqual(any(LocalDateTime.class)))
                .thenReturn(List.of(vencido, outro));
        doThrow(new IllegalStateException("Falha no banco")).when(admin).excluirContaAutomatica(vencido);

        new ExclusaoAutomaticaService(pedidos, admin, email).excluirContasVencidas();

        verify(admin).excluirContaAutomatica(vencido);
        verify(admin).excluirContaAutomatica(outro);
        verify(email).enviarConfirmacaoExclusao(outro.getEmail(), outro.getNome());
        verify(email, never()).enviarConfirmacaoExclusao(vencido.getEmail(), vencido.getNome());
    }

    private Usuario usuario(boolean ativo) {
        var usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 9L);
        usuario.setEmail("aluno@teste.com");
        usuario.setAtivo(ativo);
        return usuario;
    }

    private SolicitacaoExclusao pedido(LocalDateTime data) {
        var pedido = new SolicitacaoExclusao();
        pedido.setUsuarioId(9L);
        pedido.setSolicitadaEm(data);
        pedido.setNome("Aluno");
        pedido.setEmail("aluno@teste.com");
        return pedido;
    }
}