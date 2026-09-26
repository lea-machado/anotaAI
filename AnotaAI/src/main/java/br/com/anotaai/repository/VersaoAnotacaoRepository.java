package br.com.anotaai.repository;

import br.com.anotaai.model.VersaoAnotacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersaoAnotacaoRepository extends JpaRepository<VersaoAnotacao, Long> {
    List<VersaoAnotacao> findByAnotacaoIdOrderByCriadoEmAsc(Long anotacaoId);
}
