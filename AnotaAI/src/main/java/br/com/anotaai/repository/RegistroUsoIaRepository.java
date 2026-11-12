package br.com.anotaai.repository;

import br.com.anotaai.model.RegistroUsoIa;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;

public interface RegistroUsoIaRepository extends MongoRepository<RegistroUsoIa, Long> {
    long countByCriadoEmBetween(LocalDateTime inicio, LocalDateTime fim);
    void deleteByUsuarioId(Long usuarioId);
}
