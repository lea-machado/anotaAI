package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPrivacidadeTest {
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final AnotacaoRepository anotacoes = mock(AnotacaoRepository.class);
    final VersaoAnotacaoRepository versoes = mock(VersaoAnotacaoRepository.class);
    final BaralhoRepository baralhos = mock(BaralhoRepository.class);
    final CodigoAutenticacaoRepository codigos = mock(CodigoAutenticacaoRepository.class);
    final CodigoRecuperacaoRepository recuperacao = mock(CodigoRecuperacaoRepository.class);
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final AdminService service = new AdminService(usuarios, anotacoes,
            versoes, baralhos, codigos, recuperacao, mock(RegistroAuditoriaRepository.class),
            mock(RegistroUsoIaRepository.class), mongo);
    Usuario admin, u;

    @BeforeEach
    void preparar() {
        admin = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(admin, "id", 1L);
        u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 2L);
        u.setEmail("pessoa@example.com");
        when(usuarios.findById(2L)).thenReturn(Optional.of(u));
        when(usuarios.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void anonimizaDadosPessoaisEExcluiEstudos() {
        assertThat(service.anonimizar(2L, admin)).isSameAs(u);
        assertThat(u.isAnonimizado()).isTrue();
        assertThat(u.isAtivo()).isFalse();
        assertThat(u.getAtivo()).isFalse();
        assertThat(u.getEmail()).startsWith("anonimo-").endsWith("@anonimo.invalid");
        assertThat(u.getSenha()).isNull();
        assertThat(u.getDataNascimento()).isNull();
        assertThat(u.getCriadoEm()).isNull();
        assertThat(u.getEscolaridade()).isNull();
        assertThat(u.getTermosAceitosEm()).isNull();
        assertThat(u.getMaioridadeDeclaradaEm()).isNull();
        assertThat(u.getVersaoTermos()).isNull();
        verify(anotacoes).deleteByUsuarioId(2L);
        verify(baralhos).deleteByUsuarioId(2L);
        verify(codigos).deleteByUsuarioId(2L);
        verify(recuperacao).deleteByUsuarioId(2L);
        var queries = org.mockito.ArgumentCaptor.forClass(Query.class);
        var updates = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateMulti(queries.capture(), updates.capture(), any(Class.class));
        assertThat(queries.getAllValues().get(0).getQueryObject().get("usuario.$id")).isEqualTo(2L);
        assertThat(updates.getAllValues().get(0).getUpdateObject().get("$unset", org.bson.Document.class))
                .containsKeys("usuario", "email", "detalhes", "enderecoIp", "userAgent");
        service.anonimizar(2L, admin);
        verify(usuarios, times(1)).save(u);
    }

    @Test
    void excluiVersoesEBaralhosDeCadaAnotacaoAntesDeAnonimizar() {
        var primeira = new Anotacao();
        var segunda = new Anotacao();
        org.springframework.test.util.ReflectionTestUtils.setField(primeira, "id", 11L);
        org.springframework.test.util.ReflectionTestUtils.setField(segunda, "id", 12L);
        when(anotacoes.findByUsuarioIdOrderByCriadoEmDesc(2L)).thenReturn(List.of(primeira, segunda));

        service.anonimizar(2L, admin);

        verify(versoes).deleteByAnotacaoId(11L);
        verify(versoes).deleteByAnotacaoId(12L);
        verify(baralhos).deleteByAnotacaoId(11L);
        verify(baralhos).deleteByAnotacaoId(12L);
        var ordem = inOrder(versoes, baralhos, anotacoes, usuarios);
        ordem.verify(versoes).deleteByAnotacaoId(11L);
        ordem.verify(baralhos).deleteByAnotacaoId(11L);
        ordem.verify(versoes).deleteByAnotacaoId(12L);
        ordem.verify(baralhos).deleteByAnotacaoId(12L);
        ordem.verify(baralhos).deleteByUsuarioId(2L);
        ordem.verify(anotacoes).deleteByUsuarioId(2L);
        ordem.verify(usuarios).save(u);
    }

    @Test
    void anonimizaTambemSemDependenciasOpcionais() {
        assertThat(new AdminService(usuarios).anonimizar(2L, admin).isAnonimizado()).isTrue();
    }

    @Test
    void statusAlternaEBloqueiaContaAnonimizada() {
        assertThat(service.alternarAtivo(2L, admin).isAtivo()).isFalse();
        assertThat(service.alternarAtivo(2L, admin).isAtivo()).isTrue();
        u.setAnonimizado(true);
        assertThatThrownBy(() -> service.alternarAtivo(2L, admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.alterarPerfil(2L, "USER", admin)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejeitaIdentificadorAusenteInexistenteEProprio() {
        assertThatThrownBy(() -> service.alternarAtivo(null, admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alternarAtivo(1L, admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alternarAtivo(99L, admin)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.alterarPerfil(null, "USER", admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alterarPerfil(2L, null, admin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.alterarPerfil(99L, "USER", admin))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(usuarios, never()).save(any());
    }
}