package br.com.anotaai.service;

import br.com.anotaai.model.CodigoRecuperacao;
import br.com.anotaai.repository.CodigoRecuperacaoRepository;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RecuperacaoSenhaService {
    private final CodigoRecuperacaoRepository codigos;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final GmailApiEmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public RecuperacaoSenhaService(CodigoRecuperacaoRepository codigos, UsuarioRepository usuarios,
                                   PasswordEncoder encoder, GmailApiEmailService emailService) {
        this.codigos = codigos;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.emailService = emailService;
    }

    @Transactional
    public UUID solicitar(String email) {
        var normalizado = UsuarioService.validarEmail(email);
        var desafio = UUID.randomUUID();
        var usuario = usuarios.buscarParaRecuperacao(normalizado).orElse(null);
        // A resposta publica nao revela se o e-mail esta cadastrado.
        if (usuario == null) return desafio;
        var agora = LocalDateTime.now();
        var ultimo = codigos.findTopByUsuarioIdOrderByCriadoEmDesc(usuario.getId());
        if (ultimo.isPresent() && ultimo.get().getCriadoEm().plusMinutes(1).isAfter(agora)) {
            return desafio;
        }
        codigos.findByUsuarioIdAndUsadoEmIsNull(usuario.getId()).forEach(anterior -> {
            anterior.setUsadoEm(agora);
            codigos.save(anterior);
        });
        var codigo = "%06d".formatted(random.nextInt(1_000_000));
        var registro = new CodigoRecuperacao();
        registro.setDesafioId(desafio);
        registro.setUsuario(usuario);
        registro.setCodigoHash(encoder.encode(codigo));
        registro.setCriadoEm(agora);
        registro.setExpiraEm(agora.plusMinutes(10));
        codigos.save(registro);
        emailService.enviarCodigoRecuperacao(usuario.getEmail(), usuario.getNome(), codigo, 10);
        return desafio;
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void redefinir(UUID desafioId, String codigo, String novaSenha) {
        UsuarioService.validarSenha(novaSenha);
        if (desafioId == null) throw invalido();
        var registro = codigos.findByDesafioId(desafioId).orElseThrow(this::invalido);
        var agora = LocalDateTime.now();
        if (registro.getUsadoEm() != null || !agora.isBefore(registro.getExpiraEm())
                || registro.getTentativas() >= 5) throw invalido();
        if (codigo == null || !codigo.matches("[0-9]{6}") || !encoder.matches(codigo, registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            codigos.save(registro);
            throw invalido();
        }
        registro.getUsuario().setSenha(encoder.encode(novaSenha));
        usuarios.save(registro.getUsuario());
        registro.setUsadoEm(agora);
        codigos.save(registro);
    }

    private IllegalArgumentException invalido() {
        return new IllegalArgumentException("Código inválido, expirado ou bloqueado. Confira o código ou solicite outro.");
    }
}
