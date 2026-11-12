package br.com.anotaai.repository;

import br.com.anotaai.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, Long> {

    default Optional<Usuario> buscarParaRecuperacao(String email) { return findByEmailIgnoreCase(email); }
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
