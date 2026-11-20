package br.com.anotaai.config;

import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.SolicitacaoExclusaoService;
import br.com.anotaai.service.TermosService;
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
        SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioRepository usuarioRepository,
                        SolicitacaoExclusaoService exclusaoService, TermosService termosService) throws Exception {
                var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                csrfRepository.setCookiePath("/");

                http
                                .authorizeHttpRequests(regras -> regras
                                                .requestMatchers(
                                                                "/pages/login.html", "/pages/cadastro.html", "/pages/verificar-email.html",
                                                                "/pages/privacidade.html",
                                                                "/pages/conta-em-exclusao.html",
                                                                "/js/conta-em-exclusao.js",
                                                                "/js/aceite-termos.js", "/js/admin-termos.js",
                                                                "/css/**", "/assets/**", "/js/api.js", "/js/login.js",
                                                                "/js/cadastro.js", "/js/verificar-email.js", "/js/password-visibility.js",
                                                                "/js/termos.js",
                                                                "/api/auth/login", "/api/auth/2fa/verify",
                                                                "/api/auth/cadastro", "/api/auth/cadastro/confirmar",
                                                                "/api/auth/cadastro/reenviar", "/api/auth/csrf",
                                                                "/error",
                                                                "/api/termos/atuais", "/api/termos/versoes",
                                                                "/api/termos/versoes/**",
                                                                "/api/auth/recuperacao/solicitar",
                                                                "/api/auth/recuperacao/redefinir",
                                                                "/pages/recuperar-senha.html", "/js/recuperar-senha.js")
                                                .permitAll()
                                                .requestMatchers("/api/auth/logout").authenticated()
                                                .requestMatchers("/pages/aceite-termos.html", "/api/termos/status",
                                                                "/api/termos/aceitar")
                                                .access((authentication, context) -> {
                                                        var autenticacao = authentication.get();
                                                        var ativo = autenticacao != null
                                                                        && autenticacao.isAuthenticated()
                                                                        && usuarioRepository.findByEmailIgnoreCase(
                                                                                        autenticacao.getName())
                                                                                        .map(usuario -> usuario
                                                                                                        .isAtivo()
                                                                                                        && ("USER".equals(
                                                                                                                        usuario.getPerfil())
                                                                                                                        || "ADMIN".equals(
                                                                                                                                        usuario.getPerfil())))
                                                                                        .orElse(false);
                                                        return new AuthorizationDecision(ativo);
                                                })
                                                .requestMatchers("/api/auth/exclusao/status",
                                                                "/api/auth/exclusao/recuperar")
                                                .access((authentication, context) -> {
                                                        var auth = authentication.get();
                                                        var session = context.getRequest().getSession(false);
                                                        if (auth == null || !auth.isAuthenticated() || session == null)
                                                                return new AuthorizationDecision(false);
                                                        var autorizado = usuarioRepository
                                                                        .findByEmailIgnoreCase(auth.getName())
                                                                        .filter(usuario -> !usuario.isAtivo())
                                                                        .filter(usuario -> usuario.getId().equals(
                                                                                        session.getAttribute(
                                                                                                        "EXCLUSAO_RECUPERACAO_USUARIO_ID")))
                                                                        .filter(usuario -> exclusaoService
                                                                                        .pedidoVigente(usuario.getId())
                                                                                        .isPresent())
                                                                        .isPresent();
                                                        return new AuthorizationDecision(autorizado);
                                                })
                                                .requestMatchers("/pages/admin.html", "/api/admin/**")
                                                .access((authentication, context) -> {
                                                        var autenticacao = authentication.get();
                                                        var autorizado = autenticacao != null
                                                                        && autenticacao.isAuthenticated()
                                                                        && usuarioRepository.findByEmailIgnoreCase(
                                                                                        autenticacao.getName())
                                                                                        .map(usuario -> usuario
                                                                                                        .isAtivo()
                                                                                                        && "ADMIN".equals(
                                                                                                                        usuario.getPerfil())
                                                                                                        && termosService.aceitouVigentes(
                                                                                                                        usuario))
                                                                                        .orElse(false);
                                                        return new AuthorizationDecision(autorizado);
                                                })
                                                .anyRequest().access((authentication, context) -> {
                                                        var autenticacao = authentication.get();
                                                        var ativo = autenticacao != null
                                                                        && autenticacao.isAuthenticated()
                                                                        && usuarioRepository.findByEmailIgnoreCase(
                                                                                        autenticacao.getName())
                                                                                        .map(usuario -> usuario
                                                                                                        .isAtivo()
                                                                                                        && termosService.aceitouVigentes(
                                                                                                                        usuario)
                                                                                                        && ("USER".equals(
                                                                                                                        usuario.getPerfil())
                                                                                                                        || "ADMIN".equals(
                                                                                                                                        usuario.getPerfil())))
                                                                                        .orElse(false);
                                                        return new AuthorizationDecision(ativo);
                                                }))
                                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, exception) -> {
                                                        if (request.getRequestURI().startsWith("/api/")) {
                                                                response.sendError(401, "Autenticacao obrigatoria.");
                                                        } else {
                                                                response.sendRedirect("/pages/login.html");
                                                        }
                                                }))
                                .exceptionHandling(exceptions -> exceptions
                                                .accessDeniedHandler((request, response, exception) -> {
                                                        var autenticacao = org.springframework.security.core.context.SecurityContextHolder
                                                                        .getContext().getAuthentication();
                                                        var pendente = autenticacao != null
                                                                        && autenticacao.isAuthenticated()
                                                                        && usuarioRepository.findByEmailIgnoreCase(
                                                                                        autenticacao.getName())
                                                                                        .filter(usuario -> usuario
                                                                                                        .isAtivo()
                                                                                                        && !termosService
                                                                                                                        .aceitouVigentes(
                                                                                                                                        usuario))
                                                                                        .isPresent();
                                                        if (pendente && request.getRequestURI().startsWith("/api/")) {
                                                                response.setStatus(403);
                                                                response.setContentType(
                                                                                "application/json;charset=UTF-8");
                                                                response.getWriter().write(
                                                                                "{\"codigo\":\"TERMOS_PENDENTES\",\"mensagem\":\"É necessário aceitar os termos atuais.\"}");
                                                        } else if (pendente) {
                                                                response.sendRedirect("/pages/aceite-termos.html");
                                                        } else {
                                                                response.sendError(403);
                                                        }
                                                }))
                                .sessionManagement(session -> session
                                                .sessionFixation(fixation -> fixation.migrateSession()))
                                .logout(logout -> logout.disable());

                return http.build();
        }

        @Bean
        UserDetailsService userDetailsService(UsuarioRepository usuarioRepository,
                        SolicitacaoExclusaoService exclusaoService) {
                return email -> usuarioRepository.findByEmailIgnoreCase(email)
                                .filter(usuario -> usuario.isAtivo()
                                                || exclusaoService.pedidoVigente(usuario.getId()).isPresent())
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

