package br.com.anotaai.service;

import br.com.anotaai.model.CodigoAutenticacao;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.CodigoAutenticacaoRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AutenticacaoDoisFatoresService {
    private static final int MINUTOS_VALIDADE = 5;
    private static final int MAXIMO_TENTATIVAS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final CodigoAutenticacaoRepository codigoRepository;
    private final PasswordEncoder passwordEncoder;
    private final GmailApiEmailService emailService;

    public AutenticacaoDoisFatoresService(CodigoAutenticacaoRepository codigoRepository,
                                           PasswordEncoder passwordEncoder,
                                           GmailApiEmailService emailService) {
        this.codigoRepository = codigoRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public Desafio criarDesafio(Usuario usuario) {
        var agora = LocalDateTime.now();
        codigoRepository.findTopByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).ifPresent(ultimo -> {
            if (ultimo.getCriadoEm().plusMinutes(1).isAfter(agora)) {
                throw new IllegalArgumentException("Aguarde um minuto antes de solicitar outro codigo.");
            }
        });
        codigoRepository.findByUsuarioIdAndUsadoEmIsNull(usuario.getId()).forEach(anterior -> {
            anterior.setUsadoEm(agora);
            codigoRepository.save(anterior);
        });

        var codigo = "%06d".formatted(secureRandom.nextInt(1_000_000));
        var registro = new CodigoAutenticacao();
        registro.setDesafioId(UUID.randomUUID());
        registro.setUsuario(usuario);
        registro.setCodigoHash(passwordEncoder.encode(codigo));
        registro.setTentativas(0);
        registro.setCriadoEm(agora);
        registro.setExpiraEm(agora.plusMinutes(MINUTOS_VALIDADE));
        codigoRepository.save(registro);

        try {
            emailService.enviarCodigo(usuario.getEmail(), usuario.getNome(), codigo, MINUTOS_VALIDADE);
        } catch (RuntimeException exception) {
            codigoRepository.delete(registro);
            throw exception;
        }
        return new Desafio(registro.getDesafioId(), mascararEmail(usuario.getEmail()), MINUTOS_VALIDADE * 60);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Usuario validar(UUID desafioId, String codigo) {
        var registro = codigoRepository.findByDesafioId(desafioId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitacao de verificacao invalida."));
        var agora = LocalDateTime.now();

        if (registro.getUsadoEm() != null) {
            throw new IllegalArgumentException("Este codigo ja foi utilizado.");
        }
        if (agora.isAfter(registro.getExpiraEm())) {
            throw new IllegalArgumentException("O codigo expirou. Inicie o login novamente.");
        }
        if (registro.getTentativas() >= MAXIMO_TENTATIVAS) {
            throw new IllegalArgumentException("Limite de tentativas excedido. Inicie o login novamente.");
        }
        if (codigo == null || !codigo.matches("\\d{6}") || !passwordEncoder.matches(codigo, registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            codigoRepository.save(registro);
            throw new IllegalArgumentException("Codigo de verificacao invalido.");
        }

        registro.setUsadoEm(agora);
        codigoRepository.save(registro);
        return registro.getUsuario();
    }

    private String mascararEmail(String email) {
        var partes = email.split("@", 2);
        var inicio = partes[0].length() <= 2 ? partes[0].substring(0, 1) : partes[0].substring(0, 2);
        return inicio + "***@" + partes[1];
    }

    public record Desafio(UUID id, String emailMascarado, int expiraEmSegundos) {}
}
