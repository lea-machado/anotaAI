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
            "Doutorado");

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
            String versaoTermos) {
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
        usuario.setAtivo(true);
        usuario.setTermosAceitosEm(LocalDateTime.now());
        usuario.setMaioridadeDeclaradaEm(LocalDateTime.now());
        usuario.setVersaoTermos(versaoTermos);
        return usuarioRepository.save(usuario);
    }

    @Transactional

    public Usuario atualizarPerfil(Usuario usuario, String nome, String email, String escolaridade,
            String senhaAtual, String novaSenha) {
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
        if (novaSenha != null && !novaSenha.isBlank()) {
            if (senhaAtual == null || !passwordEncoder.matches(senhaAtual, usuario.getSenha())) {
                throw new IllegalArgumentException("Informe sua senha atual corretamente para alterá-la.");
            }
            validarSenha(novaSenha);
            usuario.setSenha(passwordEncoder.encode(novaSenha));
        } else if (senhaAtual != null && !senhaAtual.isBlank()) {
            throw new IllegalArgumentException("Informe a nova senha para concluir a alteração.");
        }
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
        if (senha == null || senha.codePointCount(0, senha.length()) < 12) {
            throw senhaFraca();
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no maximo 72 bytes.");
        }
        boolean maiuscula = false;
        boolean minuscula = false;
        boolean numero = false;
        boolean simbolo = false;
        for (int caractere : senha.codePoints().toArray()) {
            maiuscula |= Character.isUpperCase(caractere);
            minuscula |= Character.isLowerCase(caractere);
            numero |= Character.isDigit(caractere);
            simbolo |= !Character.isLetterOrDigit(caractere) && !Character.isWhitespace(caractere);
        }
        if (!maiuscula || !minuscula || !numero || !simbolo || contemSequenciaComum(senha)) {
            throw senhaFraca();
        }
    }

    private static IllegalArgumentException senhaFraca() {
        return new IllegalArgumentException("Use uma senha com pelo menos 12 caracteres, incluindo letra maiúscula, "
                + "letra minúscula, número e símbolo. Evite sequências simples e palavras comuns.");
    }

    private static boolean contemSequenciaComum(String senha) {
        var normalizada = senha.toLowerCase(Locale.ROOT);
        if (normalizada.contains("password") || normalizada.contains("senha") || normalizada.contains("admin")) {
            return true;
        }

        int[] caracteres = normalizada.codePoints().toArray();
        int crescente = 1;
        int decrescente = 1;
        for (int indice = 1; indice < caracteres.length; indice++) {
            int anterior = caracteres[indice - 1];
            int atual = caracteres[indice];
            boolean alfanumericosAscii = asciiAlfanumerico(anterior) && asciiAlfanumerico(atual);
            crescente = alfanumericosAscii && atual == anterior + 1 ? crescente + 1 : 1;
            decrescente = alfanumericosAscii && atual == anterior - 1 ? decrescente + 1 : 1;
            if (crescente >= 4 || decrescente >= 4)
                return true;
        }

        for (String linha : Set.of("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            for (int inicio = 0; inicio <= linha.length() - 4; inicio++) {
                var trecho = linha.substring(inicio, inicio + 4);
                if (normalizada.contains(trecho)
                        || normalizada.contains(new StringBuilder(trecho).reverse()))
                    return true;
            }
        }
        return false;
    }

    private static boolean asciiAlfanumerico(int caractere) {
        return caractere >= 'a' && caractere <= 'z' || caractere >= '0' && caractere <= '9';
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