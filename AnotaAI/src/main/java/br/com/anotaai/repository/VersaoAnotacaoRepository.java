package br.com.anotaai.repository;

import br.com.anotaai.model.VersaoAnotacao;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VersaoAnotacaoRepository extends MongoRepository<VersaoAnotacao, Long> {
    void deleteByAnotacaoId(Long anotacaoId);
    List<VersaoAnotacao> findByAnotacaoIdOrderByCriadoEmAsc(Long anotacaoId);
}
