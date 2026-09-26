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
    private final UsuarioAtualService usuarioAtualService;

    public DesempenhoService(AnotacaoRepository anotacaoRepository, UsuarioAtualService usuarioAtualService) {
        this.anotacaoRepository = anotacaoRepository;
        this.usuarioAtualService = usuarioAtualService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obter() {
        var anotacoes = anotacaoRepository.findByUsuarioIdOrderByAtualizadoEmDesc(usuarioAtualService.obter().getId());
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
