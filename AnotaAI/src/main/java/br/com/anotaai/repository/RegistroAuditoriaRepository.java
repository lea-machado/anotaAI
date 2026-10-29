package br.com.anotaai.repository;

import br.com.anotaai.model.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findTop50ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<RegistroAuditoria> findTop100ByOrderByCriadoEmDesc();
}
