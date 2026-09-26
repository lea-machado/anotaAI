package br.com.anotaai.repository;

import br.com.anotaai.model.CodigoAutenticacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoAutenticacaoRepository extends JpaRepository<CodigoAutenticacao, Long> {
    @EntityGraph(attributePaths = "usuario")
    Optional<CodigoAutenticacao> findByDesafioId(UUID desafioId);
    Optional<CodigoAutenticacao> findTopByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<CodigoAutenticacao> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);
}
