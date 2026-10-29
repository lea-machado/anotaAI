package br.com.anotaai.repository;

import br.com.anotaai.model.Anotacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnotacaoRepository extends JpaRepository<Anotacao, Long> {
    List<Anotacao> findByUsuarioIdAndTituloContainingIgnoreCaseOrderByAtualizadoEmDesc(Long usuarioId, String titulo);
    List<Anotacao> findByUsuarioIdOrderByAtualizadoEmDesc(Long usuarioId);
    java.util.Optional<Anotacao> findByIdAndUsuarioId(Long id, Long usuarioId);
    long countByUsuarioId(Long usuarioId);
}
