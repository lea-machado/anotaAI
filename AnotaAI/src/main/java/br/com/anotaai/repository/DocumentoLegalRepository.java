package br.com.anotaai.repository;

import br.com.anotaai.model.DocumentoLegal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentoLegalRepository extends MongoRepository<DocumentoLegal, String> {
    Optional<DocumentoLegal> findTopByTipoOrderByNumeroDesc(String tipo);
    List<DocumentoLegal> findByTipoOrderByNumeroDesc(String tipo);
}
