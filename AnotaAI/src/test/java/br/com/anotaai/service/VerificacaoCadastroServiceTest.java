package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificacaoCadastroServiceTest {
    UsuarioRepository repository = mock(UsuarioRepository.class);
    GmailApiEmailService email = mock(GmailApiEmailService.class);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    Clock clock = mock(Clock.class);
    VerificacaoCadastroService service;
    String link;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:00:00Z"));
        service = new VerificacaoCadastroService(new UsuarioService(repository, encoder), email,
                "https://anotaai.example/", clock);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        doAnswer(i -> { link = i.getArgument(2); return null; }).when(email)
                .enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
    }

    VerificacaoCadastroService.Desafio iniciar() {
        return service.iniciar("Estudante", " ESTUDANTE@example.com ", "Conta!Forte2026",
                LocalDate.of(2000, 1, 1), "Ensino Superior Incompleto", true, true, UsuarioService.VERSAO_ATUAL_TERMOS);
    }

    String token() { return link.split("#token=", 2)[1]; }

    @Test
    void configuracaoDaUrlUsaRenderEPermiteSobrescreverEndereco() throws Exception {
        var properties = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(
                new org.springframework.core.io.ClassPathResource("application.properties"));
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("app.public-url", properties.getProperty("app.public-url"))
                .withProperty("server.port", "8080");
        assertThat(environment.getProperty("app.public-url")).isEqualTo("https://anotaai-ctsf.onrender.com");
        for (var configuracao : java.util.Map.of(
                "ia.limite.tokens-por-periodo", "50000",
                "ia.limite.periodo-segundos", "86400",
                "ia.limite.max-tokens-saida", "4096").entrySet()) {
            environment.setProperty(configuracao.getKey(), properties.getProperty(configuracao.getKey()));
            assertThat(environment.getProperty(configuracao.getKey())).isEqualTo(configuracao.getValue());
        }
        environment.setProperty("IA_TOKENS_POR_PERIODO", "20000");
        assertThat(environment.getProperty("ia.limite.tokens-por-periodo", Long.class)).isEqualTo(20000L);
        environment.setProperty("ANOTAAI_PUBLIC_URL", "http://localhost:8080");
        assertThat(environment.getProperty("app.public-url")).isEqualTo("http://localhost:8080");
        environment.setProperty("ANOTAAI_PUBLIC_URL", "https://anotaai.example");
        assertThat(environment.getProperty("app.public-url")).isEqualTo("https://anotaai.example");
    }

    @Test
    void soPersisteAposUsarOTokenSecretoDoEmail() {
        var desafio = iniciar();
        verify(repository, never()).save(any());
        assertThat(desafio.expiraEmSegundos()).isEqualTo(600);
        assertThat(link).startsWith("https://anotaai.example/pages/verificar-email.html#token=");
        assertThat(token()).hasSize(43).isNotEqualTo(desafio.desafioId());
        assertThatThrownBy(() -> service.confirmar(desafio.desafioId())).isInstanceOf(IllegalArgumentException.class);
        var usuario = service.confirmar(token());
        assertThat(usuario.getEmail()).isEqualTo("estudante@example.com");
        assertThat(usuario.getPerfil()).isEqualTo("USER");
        assertThat(encoder.matches("Conta!Forte2026", usuario.getSenha())).isTrue();
        verify(repository).save(usuario);
        assertThatThrownBy(() -> service.confirmar(token())).isInstanceOf(IllegalArgumentException.class);
        verify(repository, times(1)).save(any());
    }

    @Test
    void expiraExatamenteAosDezMinutosSemSalvar() {
        iniciar();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:10:00Z"));
        assertThatThrownBy(() -> service.confirmar(token())).hasMessageContaining("expirada");
        verify(repository, never()).save(any());
        iniciar();
    }

    @Test
    void falhaDeEnvioNaoSalvaNemBloqueiaNovaSolicitacao() {
        doThrow(new ServicoEmailException("Falha", new RuntimeException())).when(email)
                .enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
        assertThatThrownBy(this::iniciar).isInstanceOf(ServicoEmailException.class);
        verify(repository, never()).save(any());
        doNothing().when(email).enviarVerificacaoCadastro(anyString(), anyString(), anyString(), anyInt());
        assertThatCode(this::iniciar).doesNotThrowAnyException();
    }

    @Test
    void reenvioRespeitaIntervaloENaoProlongaValidade() {
        var desafio = iniciar();
        var primeiroLink = link;
        assertThatThrownBy(() -> service.reenviar(desafio.desafioId())).hasMessageContaining("um minuto");
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:01:00Z"));
        service.reenviar(desafio.desafioId());
        assertThat(link).isEqualTo(primeiroLink);
        verify(email).enviarVerificacaoCadastro(anyString(), anyString(), eq(link), eq(9));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:10:00Z"));
        assertThatThrownBy(() -> service.confirmar(token())).hasMessageContaining("expirada");
        verify(repository, never()).save(any());
    }

    @Test
    void naoPermiteSubstituirCadastroPendenteNemConfirmarTokenAusente() {
        iniciar();
        assertThatThrownBy(this::iniciar).hasMessageContaining("Já enviamos");
        assertThatThrownBy(() -> service.confirmar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirmar("invalido")).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void revalidaEmailDuplicadoAntesDeSalvar() {
        iniciar();
        when(repository.existsByEmailIgnoreCase("estudante@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.confirmar(token())).hasMessageContaining("Ja existe");
        verify(repository, never()).save(any());
    }

    @Test
    void exigeNovoAceiteQuandoTermosMudamAntesDaConfirmacao() {
        var termos = mock(TermosService.class);
        var documento = new br.com.anotaai.model.DocumentoLegal();
        documento.setVersao(UsuarioService.VERSAO_ATUAL_TERMOS);
        when(termos.atual()).thenReturn(documento);
        service = new VerificacaoCadastroService(new UsuarioService(repository, encoder, termos), email,
                "https://anotaai.example", clock);
        iniciar();
        documento.setVersao("v2");
        assertThatThrownBy(() -> service.confirmar(token())).hasMessageContaining("Termos de Uso");
        verify(repository, never()).save(any());
    }

    @Test
    void confirmaUmaUnicaVezMesmoComCliquesConcorrentes() throws Exception {
        iniciar();
        var token = token();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeiro = executor.submit(() -> { try { service.confirmar(token); return true; }
                catch (IllegalArgumentException e) { return false; } });
            var segundo = executor.submit(() -> { try { service.confirmar(token); return true; }
                catch (IllegalArgumentException e) { return false; } });
            assertThat(primeiro.get() ^ segundo.get()).isTrue();
        }
        verify(repository, times(1)).save(any());
    }
}
