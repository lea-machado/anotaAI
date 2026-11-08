package br.com.anotaai.service;

import br.com.anotaai.model.RegistroAuditoria;
import br.com.anotaai.repository.RegistroAuditoriaRepository;
import br.com.anotaai.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {
    private final RegistroAuditoriaRepository registroRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(RegistroAuditoriaRepository registroRepository, UsuarioRepository usuarioRepository) {
        this.registroRepository = registroRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String email, String acao, String recurso, boolean sucesso,
                          String detalhes, HttpServletRequest request) {
        var registro = new RegistroAuditoria();
        if (email != null && !email.isBlank()) {
            usuarioRepository.findByEmailIgnoreCase(email).ifPresent(registro::setUsuario);
        }
        registro.setAcao(limitar(acao, 60));
        registro.setRecurso(limitar(recurso, 255));
        registro.setSucesso(sucesso);
        registro.setCriadoEm(LocalDateTime.now());
        registroRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> listarDoUsuario(Long usuarioId) {
        return registroRepository.findTop50ByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<RegistroAuditoria> listarTodos() {
        return registroRepository.findTop100ByOrderByCriadoEmDesc();
    }

    private String limitar(String valor, int limite) {
        if (valor == null) return null;
        return valor.length() <= limite ? valor : valor.substring(0, limite);
    }
}
