package br.com.anotaai.controller;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacaoControllerTest {
    final AuthenticationManager manager = mock(AuthenticationManager.class);
    final UsuarioAtualService atual = mock(UsuarioAtualService.class);
    final AuditoriaService auditoria = mock(AuditoriaService.class);
    final AutenticacaoDoisFatoresService doisFatores = mock(AutenticacaoDoisFatoresService.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final UsuarioService service = mock(UsuarioService.class);
    final SolicitacaoExclusaoService exclusao = mock(SolicitacaoExclusaoService.class);
    final GmailApiEmailService email = mock(GmailApiEmailService.class);
    final AutenticacaoController controller = new AutenticacaoController(manager, atual, auditoria, doisFatores,
            usuarios, service, exclusao, email);
    Usuario u;

    @BeforeEach
    void preparar() {
        u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 1L);
        u.setNome("Pessoa");
        u.setEmail("pessoa@example.com");
        u.setSenha("hash");
        u.setPerfil("USER");
        u.setCriadoEm(LocalDateTime.now());
        when(atual.obter()).thenReturn(u);
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void csrfRetornaCabecalhoEToken() {
        assertThat(controller.csrf(new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token")))
                .containsEntry("token", "token").containsEntry("headerName", "X-CSRF-TOKEN");
    }

    @Test
    void loginInvalidaSessaoAnteriorENaoAutenticaAntesDoSegundoFator() {
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        request.setSession(session);
        var authentication = new UsernamePasswordAuthenticationToken(u.getEmail(), "hash");
        when(manager.authenticate(any())).thenReturn(authentication);
        when(usuarios.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        var id = UUID.randomUUID();
        when(doisFatores.criarDesafio(u))
                .thenReturn(new AutenticacaoDoisFatoresService.Desafio(id, "pe***@example.com", 300));
        var result = controller.login(new AutenticacaoController.LoginRequest(" PESSOA@EXAMPLE.COM ", "senha"),
                request);
        assertThat(result.getStatusCode().value()).isEqualTo(202);
        assertThat(session.isInvalid()).isTrue();
        assertThat(((Map<?, ?>) result.getBody()).get("desafioId")).isEqualTo(id);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(manager).authenticate(argThat(a -> a.getName().equals(u.getEmail())));
    }

    @Test
    void credenciaisInvalidasRetornam401ERegistramFalha() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("inválido"));
        assertThat(
                controller.login(new AutenticacaoController.LoginRequest(null, "senha"), new MockHttpServletRequest())
                        .getStatusCode().value())
                .isEqualTo(401);
        verify(auditoria).registrar(eq(""), eq("LOGIN_FALHA"), anyString(), eq(false), anyString(), any());
    }

    @Test
    void usuarioAusenteAposAutenticacaoNaoRecebeDesafio() {
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(u.getEmail(), "hash"));
        assertThatThrownBy(() -> controller.login(new AutenticacaoController.LoginRequest(u.getEmail(), "senha"),
                new MockHttpServletRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(doisFatores);
    }

    @Test
    void segundoFatorCriaContextoERenovaSessaoExistente() {
        var id = UUID.randomUUID();
        when(doisFatores.validar(id, "246810")).thenReturn(u);
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        session.setNew(false);
        request.setSession(session);
        String anterior = session.getId();
        assertThat(controller.verificarCodigo(new AutenticacaoController.VerificacaoRequest(id, "246810"), request)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(session.getId()).isNotEqualTo(anterior);
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(u.getEmail());
    }

    @Test
    void segundoFatorEmContaPendenteCriaApenasAcessoDeRecuperacao() {
        u.setAtivo(false);
        var pedido = new SolicitacaoExclusao();
        pedido.setSolicitadaEm(LocalDateTime.now().minusDays(1));
        when(exclusao.pedidoVigente(1L)).thenReturn(Optional.of(pedido));
        var id = UUID.randomUUID();
        when(doisFatores.validar(id, "246810")).thenReturn(u);
        var request = new MockHttpServletRequest();
        var resposta = controller.verificarCodigo(new AutenticacaoController.VerificacaoRequest(id, "246810"), request);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) resposta.getBody()).get("contaEmExclusao")).isEqualTo(true);
        assertThat(request.getSession().getAttribute("EXCLUSAO_RECUPERACAO_USUARIO_ID")).isEqualTo(1L);
    }

    @Test
    void segundoFatorInvalidoNaoCriaSessao() {
        var request = new MockHttpServletRequest();
        when(doisFatores.validar(any(), any())).thenThrow(new IllegalArgumentException("Código expirado"));
        assertThat(controller
                .verificarCodigo(new AutenticacaoController.VerificacaoRequest(UUID.randomUUID(), "000000"), request)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void perfilRetornaEscolaridadeDoUsuarioAutenticado() {
        assertThat(controller.usuarioAtual()).containsEntry("escolaridade", null);
        u.setEscolaridade("Ensino Médio Completo");
        assertThat(controller.usuarioAtual()).containsEntry("email", u.getEmail())
                .containsEntry("escolaridade", "Ensino Médio Completo")
                .containsOnlyKeys("id", "nome", "email", "perfil", "escolaridade", "criadoEm");
    }

    @Test
    void atualizacaoDePerfilAtualizaSessao() {
        var dados = new AutenticacaoController.AtualizarPerfilRequest("Pessoa", u.getEmail(), "Mestrado", null, null);
        when(service.atualizarPerfil(u, "Pessoa", u.getEmail(), "Mestrado", null, null)).thenAnswer(chamada -> {
            u.setEscolaridade(chamada.getArgument(3));
            return u;
        });
        var request = new MockHttpServletRequest();
        var resposta = controller.atualizarUsuarioAtual(dados, request);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(controller.usuarioAtual());
        assertThat(controller.usuarioAtual()).containsEntry("escolaridade", "Mestrado");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(u.getEmail());
        assertThat(request.getSession(false)).isNotNull();
        when(service.atualizarPerfil(u, "Pessoa", u.getEmail(), "Mestrado", null, null))
                .thenThrow(new IllegalArgumentException("Email duplicado"));
        assertThat(controller.atualizarUsuarioAtual(dados, new MockHttpServletRequest()).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void exclusaoNotificaSomenteAdministradoresAtivosEEncerraSessaoMesmoSeEmailFalhar() {
        var pedido = new SolicitacaoExclusao();
        pedido.setSolicitadaEm(LocalDateTime.now());
        when(exclusao.solicitar(eq(u), any())).thenReturn(pedido);
        var admin = new Usuario();
        admin.setPerfil("ADMIN");
        admin.setEmail("admin@example.com");
        var inativo = new Usuario();
        inativo.setPerfil("ADMIN");
        inativo.setAtivo(false);
        when(usuarios.findAll()).thenReturn(List.of(u, admin, inativo));
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        request.setSession(session);
        assertThat(controller.solicitarExclusao(request).getStatusCode().value()).isEqualTo(202);
        assertThat(session.isInvalid()).isTrue();
        verify(email).enviarAvisoPedidoExclusao(admin.getEmail(), u.getNome(), u.getEmail(), pedido.getSolicitadaEm());
        doThrow(new ServicoEmailException("indisponível")).when(email).enviarAvisoPedidoExclusao(any(), any(), any(),
                any());
        assertThat(controller.solicitarExclusao(new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void pedidoDuplicadoRetornaConflito() {
        when(exclusao.solicitar(eq(u), any())).thenThrow(new IllegalStateException("Pedido existente"));
        assertThat(controller.solicitarExclusao(new MockHttpServletRequest()).getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(email);
    }

    @Test
    void recuperarContaCancelaPedidoEEncerraSessao() {
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        request.setSession(session);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), "senha"));
        when(usuarios.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        when(exclusao.recuperar(1L, request)).thenReturn(u);
        assertThat(controller.recuperarConta(request).get("mensagem")).contains("recuperada");
        verify(exclusao).recuperar(1L, request);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logoutInvalidaSessaoELimpaContexto() {
        for (boolean comSessao : List.of(true, false)) {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), "hash"));
            var request = new MockHttpServletRequest();
            var session = new MockHttpSession();
            if (comSessao)
                request.setSession(session);
            assertThat(controller.logout(request)).containsEntry("mensagem", "Sessao encerrada.");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            if (comSessao)
                assertThat(session.isInvalid()).isTrue();
        }
    }
}