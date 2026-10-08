package br.com.anotaai.service;

import br.com.anotaai.model.CodigoAutenticacao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.CodigoAutenticacaoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutenticacaoDoisFatoresServiceTest {
    private final CodigoAutenticacaoRepository repository = mock(CodigoAutenticacaoRepository.class);
    private final GmailApiEmailService emailService = mock(GmailApiEmailService.class);
    private final AutenticacaoDoisFatoresService service = new AutenticacaoDoisFatoresService(
            repository, new BCryptPasswordEncoder(4), emailService);

    @Test
    void gerarEnviarEValidarCodigoDeSeisDigitos() {
        var usuario = usuario();
        when(repository.findTopByUsuarioIdOrderByCriadoEmDesc(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var desafio = service.criarDesafio(usuario);
        var codigoCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarCodigo(
                org.mockito.ArgumentMatchers.eq(usuario.getEmail()),
                org.mockito.ArgumentMatchers.eq(usuario.getNome()),
                codigoCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(5));
        var codigo = codigoCaptor.getValue();
        assertEquals(6, codigo.length());

        var registroCaptor = ArgumentCaptor.forClass(CodigoAutenticacao.class);
        verify(repository).save(registroCaptor.capture());
        var registro = registroCaptor.getValue();
        when(repository.findByDesafioId(desafio.id())).thenReturn(Optional.of(registro));

        assertSame(usuario, service.validar(desafio.id(), codigo));
        assertNotNull(registro.getUsadoEm());
    }

    @Test
    void contabilizarCodigoIncorreto() {
        var registro = new CodigoAutenticacao();
        registro.setDesafioId(UUID.randomUUID());
        registro.setUsuario(usuario());
        registro.setCodigoHash(new BCryptPasswordEncoder(4).encode("123456"));
        registro.setCriadoEm(LocalDateTime.now());
        registro.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        when(repository.findByDesafioId(registro.getDesafioId())).thenReturn(Optional.of(registro));

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class, () -> service.validar(registro.getDesafioId(), "654321"));
        assertEquals("Codigo de verificacao invalido.", excecao.getMessage());
        assertEquals(1, registro.getTentativas());
        verify(repository).save(registro);
    }

    @Test
    void bloquearQuandoAtingirCincoTentativas(){
        var registro = new CodigoAutenticacao();
        registro.setDesafioId(UUID.randomUUID());
        registro.setUsuario(usuario());
        registro.setCodigoHash(new BCryptPasswordEncoder(4).encode("123456"));
        registro.setTentativas(5);
        registro.setCriadoEm(LocalDateTime.now());
        registro.setExpiraEm(LocalDateTime.now().plusMinutes(5));
        when(repository.findByDesafioId(registro.getDesafioId())).thenReturn(Optional.of(registro));

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class, () -> service.validar(registro.getDesafioId(), "123456"));

        assertEquals("Limite de tentativas excedido. Inicie o login novamente.", excecao.getMessage());
    }

    private Usuario usuario() {
        var usuario = new Usuario();
        usuario.setNome("Estudante Teste");
        usuario.setEmail("estudante@anotaai.com");
        usuario.setSenha("hash");
        usuario.setPerfil("USER");
        return usuario;
    }
}
