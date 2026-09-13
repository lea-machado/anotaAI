package br.com.anotaai.repository;

import br.com.anotaai.model.Anotacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnotacaoRepository extends JpaRepository<Anotacao, Long> {
    List<Anotacao> findByTituloContainingIgnoreCaseOrderByAtualizadoEmDesc(String titulo);
    List<Anotacao> findAllByOrderByAtualizadoEmDesc();
}
