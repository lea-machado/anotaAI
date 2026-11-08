package br.com.anotaai.service;

import br.com.anotaai.model.Anotacao;
import br.com.anotaai.repository.AnotacaoRepository;
import br.com.anotaai.repository.BaralhoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DesempenhoService {
    private final AnotacaoRepository anotacaoRepository;
    private final UsuarioAtualService usuarioAtualService;
    private final BaralhoRepository baralhoRepository;

    public DesempenhoService(AnotacaoRepository anotacaoRepository, UsuarioAtualService usuarioAtualService,
                             BaralhoRepository baralhoRepository) {
        this.anotacaoRepository = anotacaoRepository;
        this.usuarioAtualService = usuarioAtualService;
        this.baralhoRepository = baralhoRepository;
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
        var baralhos = baralhoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioAtualService.obter().getId());
        var totalFlashcards = baralhos.stream().mapToInt(b -> b.getFlashcards().size()).sum();
        var acertos = baralhos.stream().flatMap(b -> b.getFlashcards().stream()).mapToInt(c -> c.getAcertos()).sum();
        var erros = baralhos.stream().flatMap(b -> b.getFlashcards().stream()).mapToInt(c -> c.getErros()).sum();
        var respondidos = acertos + erros;

        return Map.of(
                "anotacoes", totalAnotacoes,
                "caracteres", totalCaracteres,
                "mediaCaracteres", mediaCaracteres,
                "ultimaAtualizacao", ultimaAtualizacao == null ? "" : ultimaAtualizacao,
                "flashcards", totalFlashcards,
                "acertos", acertos,
                "erros", erros,
                "taxaAcertos", respondidos == 0 ? 0 : Math.round(acertos * 100.0 / respondidos),
                "taxaErros", respondidos == 0 ? 0 : Math.round(erros * 100.0 / respondidos)
        );
    }
}
