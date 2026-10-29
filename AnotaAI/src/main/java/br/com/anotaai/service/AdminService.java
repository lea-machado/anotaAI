package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
public class AdminService {
    private static final Set<String> PERFIS_PERMITIDOS = Set.of("USER", "ADMIN");

    private final UsuarioRepository usuarioRepository;

    public AdminService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Usuario alterarPerfil(Long usuarioId, String perfil, Usuario administradorAtual) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("Usuario e obrigatorio.");
        }
        var perfilNormalizado = perfil == null ? "" : perfil.trim().toUpperCase(Locale.ROOT);
        if (!PERFIS_PERMITIDOS.contains(perfilNormalizado)) {
            throw new IllegalArgumentException("Perfil deve ser USER ou ADMIN.");
        }

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado."));
        if (usuario.getId().equals(administradorAtual.getId())) {
            throw new IllegalArgumentException("Nao e permitido alterar o proprio perfil administrativo.");
        }

        usuario.setPerfil(perfilNormalizado);
        return usuarioRepository.save(usuario);
    }
}
