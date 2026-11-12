package br.com.anotaai.repository;

import br.com.anotaai.model.CodigoAutenticacao;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoAutenticacaoRepository extends MongoRepository<CodigoAutenticacao, Long> {

    Optional<CodigoAutenticacao> findByDesafioId(UUID desafioId);
    Optional<CodigoAutenticacao> findTopByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<CodigoAutenticacao> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
