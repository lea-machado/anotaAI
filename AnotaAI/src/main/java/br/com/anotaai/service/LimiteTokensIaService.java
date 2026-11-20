package br.com.anotaai.service;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class LimiteTokensIaService {
    public static final String COLECAO = "cotas_tokens_ia";
    private final MongoTemplate mongo;
    private final UsuarioAtualService usuarioAtual;
    private final long limite;
    private final long periodoSegundos;
    private final int maxSaida;
    private final Clock clock;

    @Autowired
    public LimiteTokensIaService(MongoTemplate mongo, UsuarioAtualService usuarioAtual,
            @Value("${ia.limite.tokens-por-periodo:50000}") long limite,
            @Value("${ia.limite.periodo-segundos:86400}") long periodoSegundos,
            @Value("${ia.limite.max-tokens-saida:4096}") int maxSaida) {
        this(mongo, usuarioAtual, limite, periodoSegundos, maxSaida, Clock.systemUTC());
    }

    LimiteTokensIaService(MongoTemplate mongo, UsuarioAtualService usuarioAtual, long limite,
            long periodoSegundos, int maxSaida, Clock clock) {
        if (limite < 1 || periodoSegundos < 1 || periodoSegundos > 31536000 || maxSaida < 1 || maxSaida > limite) {
            throw new IllegalArgumentException("Configure limites positivos de IA e uma saída menor ou igual à cota.");
        }
        this.mongo = mongo;
        this.usuarioAtual = usuarioAtual;
        this.limite = limite;
        this.periodoSegundos = periodoSegundos;
        this.maxSaida = maxSaida;
        this.clock = clock;
    }

    public int maxTokensSaida() { return maxSaida; }

    // A reserva é debitada atomicamente antes da chamada externa, inclusive entre instâncias do backend.
    public Reserva reservar(long tokensEntradaEstimados) {
        if (tokensEntradaEstimados < 0) throw new IllegalArgumentException("Estimativa de tokens inválida.");
        var inicio = Math.floorDiv(clock.instant().getEpochSecond(), periodoSegundos) * periodoSegundos;
        var fim = Instant.ofEpochSecond(inicio + periodoSegundos);
        if (tokensEntradaEstimados > limite - maxSaida) throw new LimiteTokensIaException(fim);
        var total = tokensEntradaEstimados + maxSaida;
        var usuarioId = usuarioAtual.obter().getId();
        var id = usuarioId + ":" + periodoSegundos + ":" + inicio;
        try {
            mongo.insert(new Document("_id", id).append("usuarioId", usuarioId)
                    .append("tokens", 0L).append("reiniciaEm", Date.from(fim))
                    .append("expiraEm", Date.from(fim.plusSeconds(172800))), COLECAO);
        } catch (DuplicateKeyException ignored) {
            // Outra chamada já criou a mesma janela. O débito condicional abaixo decide quem pode continuar.
        }
        var query = Query.query(Criteria.where("_id").is(id).and("tokens").lte(limite - total));
        var atualizado = mongo.findAndModify(query, new Update().inc("tokens", total),
                FindAndModifyOptions.options().returnNew(true), Document.class, COLECAO);
        if (atualizado == null) throw new LimiteTokensIaException(fim);
        return new Reserva(id, total);
    }

    // Sem métricas confiáveis, a reserva permanece: timeout ou resposta inválida não repõem a cota.
    public void concluir(Reserva reserva, long tokensReais) {
        if (tokensReais < 0) return;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(reserva.id())),
                new Update().inc("tokens", tokensReais - reserva.tokens()), COLECAO);
    }

    public record Reserva(String id, long tokens) { }
}
