package br.com.anotaai.repository;

import br.com.anotaai.model.Baralho;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface BaralhoRepository extends MongoRepository<Baralho, Long> {
    List<Baralho> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<Baralho> findByUsuarioIdAndAnotacaoIdOrderByCriadoEmDesc(Long usuarioId, Long anotacaoId);
    Optional<Baralho> findByIdAndUsuarioId(Long id, Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
    void deleteByAnotacaoId(Long anotacaoId);
}
