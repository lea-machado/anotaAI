package br.com.anotaai.config;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria a conta administrativa padrão somente quando ela ainda não existe. */
@Component
@Order(100)
public class AdministradorInicializador implements CommandLineRunner {
    private static final String EMAIL = "franca.jr4794@gmail.com";
    private static final String SENHA_INICIAL = "12345678";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AdministradorInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.existsByEmailIgnoreCase(EMAIL)) return;

        var administrador = new Usuario();
        administrador.setNome("Administrador");
        administrador.setEmail(EMAIL);
        administrador.setSenha(passwordEncoder.encode(SENHA_INICIAL));
        administrador.setPerfil("ADMIN");
        administrador.setAtivo(true);
        usuarioRepository.save(administrador);
    }
}
