package br.com.anotaai.repository;

import br.com.anotaai.model.CodigoRecuperacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoRecuperacaoRepository extends JpaRepository<CodigoRecuperacao, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoRecuperacao> findByDesafioId(UUID desafioId);
    Optional<CodigoRecuperacao> findTopByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<CodigoRecuperacao> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);
}
