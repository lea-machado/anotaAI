package br.com.anotaai.service;

import br.com.anotaai.model.CodigoRecuperacao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.CodigoRecuperacaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecuperacaoSenhaServiceTest {
    private final CodigoRecuperacaoRepository codigos = mock(CodigoRecuperacaoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final GmailApiEmailService email = mock(GmailApiEmailService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final RecuperacaoSenhaService service = new RecuperacaoSenhaService(codigos, usuarios, encoder, email);

    @Test
    void enviaCodigoProtegidoETrocaSenhaUmaUnicaVez() {
        var usuario = usuario();
        when(usuarios.buscarParaRecuperacao("teste@exemplo.com")).thenReturn(Optional.of(usuario));
        var desafio = service.solicitar(" TESTE@exemplo.com ");
        var codigo = ArgumentCaptor.forClass(String.class);
        verify(email).enviarCodigoRecuperacao(eq(usuario.getEmail()), eq(usuario.getNome()), codigo.capture(), eq(10));
        assertTrue(codigo.getValue().matches("[0-9]{6}"));
        var captor = ArgumentCaptor.forClass(CodigoRecuperacao.class);
        verify(codigos).save(captor.capture());
        var registro = captor.getValue();
        assertNotEquals(codigo.getValue(), registro.getCodigoHash());
        assertTrue(encoder.matches(codigo.getValue(), registro.getCodigoHash()));
        when(codigos.findByDesafioId(desafio)).thenReturn(Optional.of(registro));
        service.redefinir(desafio, codigo.getValue(), "minhaNovaSenha123");
        assertTrue(encoder.matches("minhaNovaSenha123", usuario.getSenha()));
        assertNotNull(registro.getUsadoEm());
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(desafio, codigo.getValue(), "outraSenha123"));
        verify(usuarios, times(1)).save(usuario);
    }

    @Test
    void emailDesconhecidoNaoEnviaNemRevelaCadastro() {
        assertNotNull(service.solicitar("ninguem@exemplo.com"));
        verifyNoInteractions(email, codigos);
    }

    @Test
    void bloqueiaAposCincoTentativasErradas() {
        var registro = registro();
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> service.redefinir(registro.getDesafioId(), "000000", "novaSenha123"));
        }
        assertEquals(5, registro.getTentativas());
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(registro.getDesafioId(), "123456", "novaSenha123"));
        verify(usuarios, never()).save(any());
    }

    @Test
    void rejeitaCodigoExpirado() {
        var registro = registro();
        registro.setExpiraEm(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(registro.getDesafioId(), "123456", "novaSenha123"));
        verify(usuarios, never()).save(any());
    }

    @Test
    void validaSenhaAntesDeConsumirCodigo() {
        var registro = registro();
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(registro.getDesafioId(), "123456", "curta"));
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(registro.getDesafioId(), "123456", "á".repeat(37)));
        assertNull(registro.getUsadoEm());
        verify(usuarios, never()).save(any());
    }

    @Test
    void limitaEnviosDuranteUmMinuto() {
        var registro = registro();
        when(usuarios.buscarParaRecuperacao(anyString())).thenReturn(Optional.of(registro.getUsuario()));
        when(codigos.findTopByUsuarioIdOrderByCriadoEmDesc(null)).thenReturn(Optional.of(registro));
        assertNotNull(service.solicitar("teste@exemplo.com"));
        verifyNoInteractions(email);
        verify(codigos, never()).save(any());
    }

    @Test
    void novoEnvioInvalidaCodigoAnterior() {
        var anterior = registro();
        anterior.setCriadoEm(LocalDateTime.now().minusMinutes(2));
        when(usuarios.buscarParaRecuperacao(anyString())).thenReturn(Optional.of(anterior.getUsuario()));
        when(codigos.findTopByUsuarioIdOrderByCriadoEmDesc(null)).thenReturn(Optional.of(anterior));
        when(codigos.findByUsuarioIdAndUsadoEmIsNull(null)).thenReturn(List.of(anterior));
        service.solicitar("teste@exemplo.com");
        assertNotNull(anterior.getUsadoEm());
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(anterior.getDesafioId(), "123456", "novaSenha123"));
    }

    @Test
    void desafioAusenteOuDesconhecidoNaoAlteraSenha() {
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(null, "123456", "novaSenha123"));
        assertThrows(IllegalArgumentException.class, () -> service.redefinir(UUID.randomUUID(), "123456", "novaSenha123"));
        verify(usuarios, never()).save(any());
    }

    private CodigoRecuperacao registro() {
        var registro = new CodigoRecuperacao();
        registro.setDesafioId(UUID.randomUUID());
        registro.setUsuario(usuario());
        registro.setCodigoHash(encoder.encode("123456"));
        registro.setCriadoEm(LocalDateTime.now());
        registro.setExpiraEm(LocalDateTime.now().plusMinutes(10));
        when(codigos.findByDesafioId(registro.getDesafioId())).thenReturn(Optional.of(registro));
        return registro;
    }

    private Usuario usuario() {
        var usuario = new Usuario();
        usuario.setNome("Teste");
        usuario.setEmail("teste@exemplo.com");
        usuario.setSenha(encoder.encode("senhaAnterior123"));
        return usuario;
    }
}
