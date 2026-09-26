package br.com.anotaai.service;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.repository.AnotacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DesempenhoService {
    private final AnotacaoRepository anotacaoRepository;

    public DesempenhoService(AnotacaoRepository anotacaoRepository) {
        this.anotacaoRepository = anotacaoRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obter() {
        var anotacoes = anotacaoRepository.findAll();
        var totalAnotacoes = anotacoes.size();
        var totalCaracteres = anotacoes.stream()
                .map(Anotacao::getConteudo)
                .mapToInt(String::length)
                .sum();
        var mediaCaracteres = totalAnotacoes == 0 ? 0 : Math.round((float) totalCaracteres / totalAnotacoes);
        var ultimaAtualizacao = anotacoes.stream()
                .map(Anotacao::getAtualizadoEm)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return Map.of(
                "anotacoes", totalAnotacoes,
                "caracteres", totalCaracteres,
                "mediaCaracteres", mediaCaracteres,
                "ultimaAtualizacao", ultimaAtualizacao == null ? "" : ultimaAtualizacao
        );
    }
}
