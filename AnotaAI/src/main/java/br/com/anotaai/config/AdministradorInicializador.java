package br.com.anotaai.config;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.UsuarioService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(100)
public class AdministradorInicializador implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdministradorInicializador.class);
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean redefinirSenhaExistente;
    private final String emailInicial;
    private final String senhaInicial;

    public AdministradorInicializador(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            @Value("${admin.bootstrap.reset-password:false}") boolean redefinirSenhaExistente,
            @Value("${admin.bootstrap.email:}") String emailInicial,
            @Value("${admin.bootstrap.password:}") String senhaInicial) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.redefinirSenhaExistente = redefinirSenhaExistente;
        this.emailInicial = emailInicial;
        this.senhaInicial = senhaInicial;
    }

    @Override
    public void run(String... args) {
        if (emailInicial == null || emailInicial.isBlank()) {
            log.info("Bootstrap de administrador desativado (ADMIN_BOOTSTRAP_EMAIL não configurado).");
            return;
        }
        String email = UsuarioService.validarEmail(emailInicial);
        var existente = usuarioRepository.findByEmailIgnoreCase(email);
        if (existente.isPresent()) {
            var conta = existente.get();
            if (!"ADMIN".equalsIgnoreCase(conta.getPerfil())) {
                throw new IllegalStateException(
                        "O e-mail configurado para bootstrap já pertence a uma conta não administrativa.");
            }
            if (!redefinirSenhaExistente) {
                log.info("Conta administrativa já configurada; senha preservada.");
                return;
            }
            UsuarioService.validarSenha(senhaInicial);
            conta.setSenha(passwordEncoder.encode(senhaInicial));
            conta.setAtivo(true);
            usuarioRepository.save(conta);
            log.info("Senha da conta administrativa redefinida por configuração explícita.");
            return;
        }
        UsuarioService.validarSenha(senhaInicial);
        var administrador = new Usuario();
        administrador.setNome("Administrador");
        administrador.setEmail(email);
        administrador.setSenha(passwordEncoder.encode(senhaInicial));
        administrador.setPerfil("ADMIN");
        administrador.setAtivo(true);
        usuarioRepository.save(administrador);
        log.info("Conta administrativa inicial criada.");
    }
}