package br.com.anotaai.config;

import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioRepository usuarioRepository) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/pages/login.html", "/pages/cadastro.html",
                                "/css/**", "/assets/**", "/js/login.js", "/js/cadastro.js",
                                "/api/auth/login", "/api/auth/2fa/verify", "/api/auth/cadastro", "/api/auth/csrf", "/error",
                                "/api/auth/recuperacao/solicitar", "/api/auth/recuperacao/redefinir",
                                "/pages/recuperar-senha.html", "/js/recuperar-senha.js"
                        ).permitAll()
                        .requestMatchers("/pages/admin.html", "/api/admin/**").access((authentication, context) -> {
                            var autenticacao = authentication.get();
                            var autorizado = autenticacao != null && autenticacao.isAuthenticated()
                                    && usuarioRepository.findByEmailIgnoreCase(autenticacao.getName())
                                    .map(usuario -> "ADMIN".equals(usuario.getPerfil()))
                                    .orElse(false);
                            return new AuthorizationDecision(autorizado);
                        })
                        .anyRequest().hasAnyRole("USER", "ADMIN")
                )
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(401, "Autenticacao obrigatoria.");
                    } else {
                        response.sendRedirect("/pages/login.html");
                    }
                }))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
                .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return email -> usuarioRepository.findByEmailIgnoreCase(email)
                .map(usuario -> User.withUsername(usuario.getEmail())
                        .password(usuario.getSenha())
                        .roles(usuario.getPerfil())
                        .build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "Usuario nao encontrado."));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
