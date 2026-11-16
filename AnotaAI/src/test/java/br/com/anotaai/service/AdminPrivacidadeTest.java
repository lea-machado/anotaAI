package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPrivacidadeTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final CodigoAutenticacaoRepository codigos = mock(CodigoAutenticacaoRepository.class);
    final CodigoRecuperacaoRepository recuperacao = mock(CodigoRecuperacaoRepository.class);
    final AnotacaoRepository anotacoes = mock(AnotacaoRepository.class);
    final VersaoAnotacaoRepository versoes = mock(VersaoAnotacaoRepository.class);
    final BaralhoRepository baralhos = mock(BaralhoRepository.class);
    final SolicitacaoExclusaoRepository pedidos = mock(SolicitacaoExclusaoRepository.class);
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final AdminService service = new AdminService(usuarios, anotacoes, versoes, baralhos, codigos, recuperacao, pedidos, mongo);
    Usuario admin, u;
    @BeforeEach void preparar() {
        admin = new Usuario(); org.springframework.test.util.ReflectionTestUtils.setField(admin,"id",1L);
        u = new Usuario(); org.springframework.test.util.ReflectionTestUtils.setField(u,"id",2L); u.setEmail("pessoa@example.com");
        when(usuarios.findById(2L)).thenReturn(Optional.of(u)); when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void excluiContaPedidoEDadosDeEstudo() {
        var pedido = new SolicitacaoExclusao();
        pedido.setUsuarioId(2L);
        var anotacao = new Anotacao();
        ReflectionTestUtils.setField(anotacao, "id", 42L);
        when(anotacoes.findByUsuarioIdOrderByCriadoEmDesc(2L)).thenReturn(List.of(anotacao));

        service.excluirConta(pedido, admin);
        verify(usuarios).delete(u);
        verify(usuarios, never()).save(any());
        verify(pedidos).delete(pedido);
        verify(codigos).deleteByUsuarioId(2L); verify(recuperacao).deleteByUsuarioId(2L);
        verify(versoes).deleteByAnotacaoId(42L);
        verify(baralhos).deleteByAnotacaoId(42L);
        verify(baralhos).deleteByUsuarioId(2L);
        verify(anotacoes).deleteByUsuarioId(2L);

        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(RegistroAuditoria.class));
        assertThat(query.getValue().getQueryObject().get("usuario.$id")).isEqualTo(2L);
        assertThat(update.getValue().getUpdateObject().get("$unset", Document.class))
                .containsKeys("usuario","email","detalhes","enderecoIp","userAgent");
        verify(mongo).updateMulti(query.capture(), update.capture(), eq(RegistroUsoIa.class));
        assertThat(query.getValue().getQueryObject().get("usuarioId")).isEqualTo(2L);
        assertThat(update.getValue().getUpdateObject().get("$unset", Document.class)).containsKey("usuarioId");

        verifyNoMoreInteractions(mongo);
    }
    @Test void naoRemovePedidoSeExclusaoDaContaFalhar() {
        var pedido = new SolicitacaoExclusao();
        pedido.setUsuarioId(2L);
        doThrow(new org.springframework.dao.OptimisticLockingFailureException("Conta alterada em paralelo"))
                .when(usuarios).delete(u);

        assertThatThrownBy(() -> service.excluirConta(pedido, admin))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        verifyNoInteractions(pedidos);
    }
    @Test void statusAlternaEBloqueiaContaAnonimizada() {
        assertThat(service.alternarAtivo(2L,admin).isAtivo()).isFalse();
        assertThat(service.alternarAtivo(2L,admin).isAtivo()).isTrue();
        u.setAnonimizado(true);
        assertThatThrownBy(() -> service.alternarAtivo(2L,admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.alterarPerfil(2L,"USER",admin)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejeitaIdentificadorAusenteInexistenteEProprio() {
        assertThatThrownBy(() -> service.alternarAtivo(null,admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alternarAtivo(1L,admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alternarAtivo(99L,admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.alterarPerfil(null,"USER",admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alterarPerfil(2L,null,admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alterarPerfil(99L,"USER",admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(usuarios,never()).save(any());
    }
}
