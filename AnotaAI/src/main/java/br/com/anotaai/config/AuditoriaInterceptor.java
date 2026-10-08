package br.com.anotaai.config;

import br.com.anotaai.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditoriaInterceptor implements HandlerInterceptor {
    private final AuditoriaService auditoriaService;

    public AuditoriaInterceptor(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return;

        var sucesso = response.getStatus() < 400 && exception == null;
        var detalhes = "HTTP " + response.getStatus();
        auditoriaService.registrar(auth.getName(), nomeDaAcao(request), request.getRequestURI(),
                sucesso, detalhes, request);
    }

    private String nomeDaAcao(HttpServletRequest request) {
        var caminho = request.getRequestURI();
        if (caminho.equals("/api/desempenho")) return "CONSULTAR_DESEMPENHO";
        if (caminho.equals("/api/auditoria")) return "CONSULTAR_AUDITORIA";
        if (caminho.startsWith("/api/admin")) return "ACESSO_ADMINISTRATIVO";
        return switch (request.getMethod()) {
            case "POST" -> "CRIAR_ANOTACAO";
            case "PUT", "PATCH" -> "ALTERAR_ANOTACAO";
            case "DELETE" -> "EXCLUIR_ANOTACAO";
            default -> caminho.endsWith("/versoes") ? "CONSULTAR_VERSOES" : "CONSULTAR_ANOTACAO";
        };
    }
}
