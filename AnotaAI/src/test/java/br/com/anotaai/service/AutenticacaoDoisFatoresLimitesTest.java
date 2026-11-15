package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.CodigoAutenticacaoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutenticacaoDoisFatoresLimitesTest {
    final CodigoAutenticacaoRepository repo = mock(CodigoAutenticacaoRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final GmailApiEmailService email = mock(GmailApiEmailService.class);
    final AutenticacaoDoisFatoresService service = new AutenticacaoDoisFatoresService(repo, encoder, email);
    Usuario u;
    CodigoAutenticacao codigo;

    @BeforeEach
    void preparar() {
        u = new Usuario();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", 1L);
        u.setEmail("a@example.com");
        u.setNome("Ana");
        codigo = new CodigoAutenticacao();
        codigo.setDesafioId(UUID.randomUUID());
        codigo.setUsuario(u);
        codigo.setCriadoEm(LocalDateTime.now().minusMinutes(2));
        codigo.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        codigo.setCodigoHash("hash");
        when(repo.findByDesafioId(codigo.getDesafioId())).thenReturn(Optional.of(codigo));
    }

    @Test
    void desafioInexistenteERejeitado() {
        assertThatThrownBy(() -> service.validar(UUID.randomUUID(), "246810")).hasMessageContaining("invalida");
    }

    @Test
    void bloqueiaReusoExpiracaoETentativasExcedidas() {
        codigo.setUsadoEm(LocalDateTime.now());
        assertThatThrownBy(() -> service.validar(codigo.getDesafioId(), "246810")).hasMessageContaining("utilizado");
        codigo.setUsadoEm(null);
        codigo.setExpiraEm(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.validar(codigo.getDesafioId(), "246810")).hasMessageContaining("expirou");
        codigo.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        codigo.setTentativas(5);
        assertThatThrownBy(() -> service.validar(codigo.getDesafioId(), "246810")).hasMessageContaining("Limite");
        verify(repo, never()).save(any());
    }

    @Test
    void contaDesativadaInvalidaCodigo() {
        u.setAtivo(false);
        assertThatThrownBy(() -> service.validar(codigo.getDesafioId(), "246810")).hasMessageContaining("desativada");
        assertThat(codigo.getUsadoEm()).isNotNull();
        verify(repo).save(codigo);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "abc", "12345", "1234567" })
    void formatoInvalidoConsomeTentativa(String valor) {
        assertThatThrownBy(() -> service.validar(codigo.getDesafioId(), valor)).hasMessageContaining("invalido");
        assertThat(codigo.getTentativas()).isEqualTo(1);
        verifyNoInteractions(encoder);
    }

    @Test
    void limitaReenvioAUmMinuto() {
        codigo.setCriadoEm(LocalDateTime.now());
        when(repo.findTopByUsuarioIdOrderByCriadoEmDesc(1L)).thenReturn(Optional.of(codigo));
        assertThatThrownBy(() -> service.criarDesafio(u)).hasMessageContaining("um minuto");
        verifyNoInteractions(email);
    }

    @Test
    void novoDesafioInvalidaAnteriorEMascaraEmailCurto() {
        when(repo.findTopByUsuarioIdOrderByCriadoEmDesc(1L)).thenReturn(Optional.of(codigo));
        when(repo.findByUsuarioIdAndUsadoEmIsNull(1L)).thenReturn(List.of(codigo));
        var desafio = service.criarDesafio(u);
        assertThat(desafio.emailMascarado()).isEqualTo("a***@example.com");
        assertThat(desafio.expiraEmSegundos()).isEqualTo(300);
        assertThat(codigo.getUsadoEm()).isNotNull();
        verify(repo).save(codigo);
    }

    @Test
    void falhaNoEnvioRemoveDesafioNovo() {
        doThrow(new ServicoEmailException("indisponível")).when(email).enviarCodigo(any(), any(), any(), anyInt());
        assertThatThrownBy(() -> service.criarDesafio(u)).isInstanceOf(ServicoEmailException.class);
        verify(repo).delete(argThat(c -> c.getDesafioId() != null && c.getUsuario() == u));
    }
}