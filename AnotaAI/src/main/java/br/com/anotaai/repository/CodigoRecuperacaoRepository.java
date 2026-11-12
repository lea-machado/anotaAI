package br.com.anotaai.repository;

import br.com.anotaai.model.CodigoRecuperacao;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoRecuperacaoRepository extends MongoRepository<CodigoRecuperacao, Long> {

    Optional<CodigoRecuperacao> findByDesafioId(UUID desafioId);
    Optional<CodigoRecuperacao> findTopByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<CodigoRecuperacao> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
