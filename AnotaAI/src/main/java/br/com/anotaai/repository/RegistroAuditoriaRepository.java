package br.com.anotaai.repository;

import br.com.anotaai.model.RegistroAuditoria;
import org.springframework.data.domain.Range;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface RegistroAuditoriaRepository extends MongoRepository<RegistroAuditoria, Long> {
    List<RegistroAuditoria> findTop50ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    List<RegistroAuditoria> findTop100ByOrderByCriadoEmDesc();
    List<RegistroAuditoria> findTop100BySucessoOrderByCriadoEmDesc(boolean sucesso);
    List<RegistroAuditoria> findTop100ByCriadoEmBetweenOrderByCriadoEmDesc(Range<LocalDateTime> periodo);
    List<RegistroAuditoria> findTop100BySucessoAndCriadoEmBetweenOrderByCriadoEmDesc(
            boolean sucesso, Range<LocalDateTime> periodo);

    long countBySucessoFalse();

    long countByCriadoEmBetween(Range<LocalDateTime> periodo);

    long countBySucessoFalseAndCriadoEmBetween(Range<LocalDateTime> periodo);

    void deleteByUsuarioId(Long usuarioId);
}
