package br.com.anotaai.repository;

import br.com.anotaai.model.RegistroAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface RegistroAuditoriaRepository extends MongoRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findTop50ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<RegistroAuditoria> findTop100ByOrderByCriadoEmDesc();
    long countBySucessoFalse();
    long countByCriadoEmBetween(LocalDateTime inicio, LocalDateTime fim);
    long countBySucessoFalseAndCriadoEmBetween(LocalDateTime inicio, LocalDateTime fim);
    void deleteByUsuarioId(Long usuarioId);
}
