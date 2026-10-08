package br.com.anotaai.controller;

import br.com.anotaai.model.RegistroAuditoria;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.RegistroAuditoriaRepository;
import br.com.anotaai.repository.UsuarioRepository;
import br.com.anotaai.service.AuditoriaService;
import br.com.anotaai.service.AdminService;
import br.com.anotaai.service.UsuarioAtualService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UsuarioRepository usuarioRepository;
    private final AnotacaoRepository anotacaoRepository;
    private final RegistroAuditoriaRepository auditoriaRepository;
    private final AuditoriaService auditoriaService;
    private final AdminService adminService;
    private final UsuarioAtualService usuarioAtualService;

    public AdminController(UsuarioRepository usuarioRepository,
                           AnotacaoRepository anotacaoRepository,
                           RegistroAuditoriaRepository auditoriaRepository,
                           AuditoriaService auditoriaService,
                           AdminService adminService,
                           UsuarioAtualService usuarioAtualService) {
        this.usuarioRepository = usuarioRepository;
        this.anotacaoRepository = anotacaoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.auditoriaService = auditoriaService;
        this.adminService = adminService;
        this.usuarioAtualService = usuarioAtualService;
    }

    @GetMapping("/resumo")
    public Map<String, Long> resumo() {
        return Map.of(
                "usuarios", usuarioRepository.count(),
                "anotacoes", anotacaoRepository.count(),
                "registrosAuditoria", auditoriaRepository.count()
        );
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        return usuarioRepository.findAll().stream().map(usuario -> Map.<String, Object>of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "perfil", usuario.getPerfil(),
                "criadoEm", usuario.getCriadoEm(),
                "anotacoes", anotacaoRepository.countByUsuarioId(usuario.getId())
        )).toList();
    }

    @PutMapping("/usuarios/{id}/perfil")
    public Map<String, Object> alterarPerfil(@PathVariable Long id,
                                              @RequestBody PerfilRequest dados,
                                              HttpServletRequest request) {
        var administrador = usuarioAtualService.obter();
        try {
            var usuario = adminService.alterarPerfil(id, dados.perfil(), administrador);
            auditoriaService.registrar(administrador.getEmail(), "ALTERACAO_PERFIL_USUARIO",
                    "/api/admin/usuarios/" + id + "/perfil", true,
                    "Perfil de " + usuario.getEmail() + " alterado para " + usuario.getPerfil() + ".", request);
            return Map.of(
                    "id", usuario.getId(),
                    "email", usuario.getEmail(),
                    "perfil", usuario.getPerfil(),
                    "mensagem", "Perfil atualizado com sucesso."
            );
        } catch (RuntimeException exception) {
            auditoriaService.registrar(administrador.getEmail(), "ALTERACAO_PERFIL_USUARIO",
                    "/api/admin/usuarios/" + id + "/perfil", false, exception.getMessage(), request);
            throw exception;
        }
    }

    @GetMapping("/auditoria")
    public List<Map<String, Object>> auditoria() {
        return auditoriaService.listarTodos().stream().map(this::mapearAuditoria).toList();
    }

    private Map<String, Object> mapearAuditoria(RegistroAuditoria registro) {
        var dados = new LinkedHashMap<String, Object>();
        dados.put("id", registro.getId());
        dados.put("email", registro.getEmail() == null ? "Não identificado" : registro.getEmail());
        dados.put("acao", registro.getAcao());
        dados.put("recurso", registro.getRecurso());
        dados.put("sucesso", registro.isSucesso());
        dados.put("enderecoIp", registro.getEnderecoIp() == null ? "-" : registro.getEnderecoIp());
        dados.put("criadoEm", registro.getCriadoEm());
        return dados;
    }

    public record PerfilRequest(String perfil) {}
}
