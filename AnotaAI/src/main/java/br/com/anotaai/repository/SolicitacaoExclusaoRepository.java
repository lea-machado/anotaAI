package br.com.anotaai.repository;

import br.com.anotaai.model.SolicitacaoExclusao;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoExclusaoRepository extends MongoRepository<SolicitacaoExclusao, Long> {
    List<SolicitacaoExclusao> findAllByOrderBySolicitadaEmAsc();
    Optional<SolicitacaoExclusao> findByUsuarioId(Long usuarioId);
}
