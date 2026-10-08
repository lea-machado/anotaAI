package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UsuarioService {
    public static final String VERSAO_ATUAL_TERMOS = "2026-09-25";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ESCOLARIDADES_VALIDAS = Set.of(
            "Ensino Fundamental Incompleto",
            "Ensino Fundamental Completo",
            "Ensino Médio Incompleto",
            "Ensino Médio Completo",
            "Ensino Superior Incompleto",
            "Ensino Superior Completo",
            "Pós-graduação",
            "Mestrado",
            "Doutorado"
    );

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario criarUsuarioComum(
            String nome,
            String email,
            String senha,
            LocalDate dataNascimento,
            String escolaridade,
            Boolean aceitouTermos,
            Boolean declarouMaioridade,
            String versaoTermos
    ) {
        var nomeValidado = validarNome(nome);
        var emailNormalizado = validarEmail(email);
        validarSenha(senha);
        validarDataNascimento(dataNascimento);
        var escolaridadeValidada = validarEscolaridade(escolaridade);
        validarAceiteTermos(aceitouTermos, versaoTermos);
        validarDeclaracaoMaioridade(declarouMaioridade);

        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new IllegalArgumentException("Ja existe uma conta cadastrada com este e-mail.");
        }

        var usuario = new Usuario();
        usuario.setNome(nomeValidado);
        usuario.setEmail(emailNormalizado);
        usuario.setDataNascimento(dataNascimento);
        usuario.setEscolaridade(escolaridadeValidada);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setPerfil("USER");
        usuario.setTermosAceitosEm(LocalDateTime.now());
        usuario.setMaioridadeDeclaradaEm(LocalDateTime.now());
        usuario.setVersaoTermos(versaoTermos);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario atualizarPerfil(Usuario usuario, String nome, String email, String escolaridade) {
        var nomeValidado = validarNome(nome);
        var emailNormalizado = validarEmail(email);
        var escolaridadeValidada = validarEscolaridade(escolaridade);

        usuarioRepository.findByEmailIgnoreCase(emailNormalizado)
                .filter(outro -> !outro.getId().equals(usuario.getId()))
                .ifPresent(outro -> {
                    throw new IllegalArgumentException("Ja existe uma conta cadastrada com este e-mail.");
                });

        usuario.setNome(nomeValidado);
        usuario.setEmail(emailNormalizado);
        usuario.setEscolaridade(escolaridadeValidada);
        return usuarioRepository.save(usuario);
    }

    public String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome e obrigatorio.");
        }
        var valor = nome.trim();
        if (valor.length() < 3 || valor.length() > 120) {
            throw new IllegalArgumentException("Nome deve ter entre 3 e 120 caracteres.");
        }
        return valor;
    }

    public static String validarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail e obrigatorio.");
        }
        var valor = email.trim().toLowerCase(Locale.ROOT);
        if (valor.length() > 180 || !EMAIL.matcher(valor).matches()) {
            throw new IllegalArgumentException("Informe um e-mail valido.");
        }
        return valor;
    }

    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no maximo 72 bytes.");
        }
    }

    public String validarEscolaridade(String escolaridade) {
        if (escolaridade == null || escolaridade.isBlank()) {
            throw new IllegalArgumentException("Escolaridade e obrigatoria.");
        }
        var valor = escolaridade.trim();
        if (!ESCOLARIDADES_VALIDAS.contains(valor)) {
            throw new IllegalArgumentException("Informe uma escolaridade valida.");
        }
        return valor;
    }

    private void validarAceiteTermos(Boolean aceitouTermos, String versaoTermos) {
        if (!Boolean.TRUE.equals(aceitouTermos) || !VERSAO_ATUAL_TERMOS.equals(versaoTermos)) {
            throw new IllegalArgumentException("E necessario ler e aceitar os Termos de Uso para criar a conta.");
        }
    }

    private void validarDataNascimento(LocalDate dataNascimento) {
        if (dataNascimento == null) {
            throw new IllegalArgumentException("A data de nascimento e obrigatoria.");
        }
        if (dataNascimento.isAfter(LocalDate.now().minusYears(18))) {
            throw new IllegalArgumentException("E necessario ter pelo menos 18 anos para criar uma conta.");
        }
    }

    private void validarDeclaracaoMaioridade(Boolean declarouMaioridade) {
        if (!Boolean.TRUE.equals(declarouMaioridade)) {
            throw new IllegalArgumentException("E necessario declarar que possui 18 anos ou mais.");
        }
    }
}
