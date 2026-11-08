package br.com.anotaai.config;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cria a conta administrativa padrão somente quando ela ainda não existe. */
@Component
@Order(100)
public class AdministradorInicializador implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdministradorInicializador.class);
    private static final String EMAIL = "franca.jr4794@gmail.com";
    private static final String SENHA_INICIAL = "12345678";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean redefinirSenhaExistente;

    public AdministradorInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                      @Value("${admin.bootstrap.reset-password:false}") boolean redefinirSenhaExistente) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.redefinirSenhaExistente = redefinirSenhaExistente;
    }

    @Override
    public void run(String... args) {
        var existente = usuarioRepository.findByEmailIgnoreCase(EMAIL);
        if (existente.isPresent()) {
            var conta = existente.get();
            if ("ADMIN".equalsIgnoreCase(conta.getPerfil())) {
                if (redefinirSenhaExistente) {
                    conta.setSenha(passwordEncoder.encode(SENHA_INICIAL));
                    conta.setAtivo(true);
                    usuarioRepository.save(conta);
                    log.info("Senha da conta administrativa atualizada por solicitação explícita.");
                    return;
                }
                log.info("Conta administrativa já está configurada.");
                return;
            }

            conta.setPerfil("ADMIN");
            conta.setSenha(passwordEncoder.encode(SENHA_INICIAL));
            conta.setAtivo(true);
            usuarioRepository.save(conta);
            log.info("Conta existente promovida para administradora.");
            return;
        }

        var administrador = new Usuario();
        administrador.setNome("Administrador");
        administrador.setEmail(EMAIL);
        administrador.setSenha(passwordEncoder.encode(SENHA_INICIAL));
        administrador.setPerfil("ADMIN");
        administrador.setAtivo(true);
        usuarioRepository.save(administrador);
        log.info("Conta administradora criada.");
    }
}
