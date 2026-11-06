package br.com.anotaai.repository;

import br.com.anotaai.model.RegistroAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RegistroAuditoriaRepository extends MongoRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findTop50ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<RegistroAuditoria> findTop100ByOrderByCriadoEmDesc();
    void deleteByUsuarioId(Long usuarioId);
}
