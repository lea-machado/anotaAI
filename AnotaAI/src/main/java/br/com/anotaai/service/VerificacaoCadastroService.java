package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificacaoCadastroService {
    private static final int MINUTOS_VALIDADE = 10;
    private final Map<String, Pendente> pendentes = new HashMap<>();
    private final UsuarioService usuarioService;
    private final GmailApiEmailService emailService;
    private final String publicUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public VerificacaoCadastroService(UsuarioService usuarioService, GmailApiEmailService emailService,
            @Value("${app.public-url:http://localhost:${server.port:8080}}") String publicUrl) {
        this(usuarioService, emailService, publicUrl, Clock.systemUTC());
    }

    VerificacaoCadastroService(UsuarioService usuarioService, GmailApiEmailService emailService,
            String publicUrl, Clock clock) {
        this.usuarioService = usuarioService;
        this.emailService = emailService;
        var uri = URI.create(publicUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Configure app.public-url com o endereço público HTTP ou HTTPS do AnotaAI.");
        }
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    public synchronized Desafio iniciar(String nome, String email, String senha, LocalDate nascimento,
            String escolaridade, Boolean aceitouTermos, Boolean maioridade, String versaoTermos) {
        limparExpirados();
        var normalizado = UsuarioService.validarEmail(email);
        if (pendentes.values().stream().anyMatch(p -> p.usuario.getEmail().equals(normalizado))) {
            throw new IllegalArgumentException(
                    "Já enviamos uma verificação para este e-mail. Confira sua caixa de entrada ou aguarde 10 minutos.");
        }
        if (pendentes.size() >= 1000) {
            throw new IllegalStateException("Muitas solicitações de cadastro. Tente novamente mais tarde.");
        }
        var usuario = usuarioService.prepararCadastro(nome, normalizado, senha, nascimento, escolaridade,
                aceitouTermos, maioridade, versaoTermos);
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var pendente = new Pendente(usuario, token, clock.instant().plusSeconds(MINUTOS_VALIDADE * 60));
        enviar(pendente);
        var desafioId = UUID.randomUUID().toString();
        pendentes.put(desafioId, pendente);
        return new Desafio(desafioId, MINUTOS_VALIDADE * 60);
    }

    public synchronized Usuario confirmar(String token) {
        limparExpirados();
        var entrada = pendentes.entrySet().stream().filter(e -> e.getValue().token.equals(token)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Verificação inválida ou expirada. Preencha o cadastro novamente."));
        var usuario = usuarioService.salvarCadastroVerificado(entrada.getValue().usuario);
        pendentes.remove(entrada.getKey());
        return usuario;
    }

    public synchronized void reenviar(String token) {
        var pendente = obter(token);
        if (pendente.enviadoEm.plusSeconds(60).isAfter(clock.instant())) {
            throw new IllegalArgumentException("Aguarde um minuto antes de solicitar outro e-mail.");
        }
        enviar(pendente);
    }

    private void enviar(Pendente pendente) {
        var segundos = pendente.expiraEm.getEpochSecond() - clock.instant().getEpochSecond();
        var minutos = (int) Math.max(1, (segundos + 59) / 60);
        var link = publicUrl + "/pages/verificar-email.html#token=" + pendente.token;
        emailService.enviarVerificacaoCadastro(pendente.usuario.getEmail(), pendente.usuario.getNome(), link, minutos);
        pendente.enviadoEm = clock.instant();
    }

    private Pendente obter(String token) {
        limparExpirados();
        var pendente = pendentes.get(token);
        if (pendente == null) {
            throw new IllegalArgumentException("Verificação inválida ou expirada. Preencha o cadastro novamente.");
        }
        return pendente;
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void limparExpirados() {
        pendentes.values().removeIf(p -> !clock.instant().isBefore(p.expiraEm));
    }

    public record Desafio(String desafioId, int expiraEmSegundos) { }

    private static class Pendente {
        final Usuario usuario;
        final Instant expiraEm;
        final String token;
        Instant enviadoEm;

        Pendente(Usuario usuario, String token, Instant expiraEm) {
            this.usuario = usuario;
            this.token = token;
            this.expiraEm = expiraEm;
        }
    }
}

