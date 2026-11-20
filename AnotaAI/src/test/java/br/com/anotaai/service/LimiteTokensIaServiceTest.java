package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LimiteTokensIaServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    UsuarioAtualService atual = mock(UsuarioAtualService.class);
    Clock clock = mock(Clock.class);
    Map<String, Document> documentos = new HashMap<>();
    LimiteTokensIaService service;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:00:00Z"));
        usuario(7L);
        service = new LimiteTokensIaService(mongo, atual, 3000, 3600, 1000, clock);
        // Simula os comandos atômicos do Mongo para exercitar a competição pela mesma cota.
        when(mongo.insert(any(Document.class), eq(LimiteTokensIaService.COLECAO))).thenAnswer(i -> {
            Document doc = i.getArgument(0);
            synchronized (documentos) {
                if (documentos.putIfAbsent(doc.getString("_id"), doc) != null) throw new DuplicateKeyException("_id");
            }
            return doc;
        });
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Document.class), eq(LimiteTokensIaService.COLECAO))).thenAnswer(i -> {
            Document filtro = ((Query) i.getArgument(0)).getQueryObject();
            Document inc = ((Update) i.getArgument(1)).getUpdateObject().get("$inc", Document.class);
            synchronized (documentos) {
                var doc = documentos.get(filtro.getString("_id"));
                long teto = ((Number) filtro.get("tokens", Document.class).get("$lte")).longValue();
                if (doc == null || doc.getLong("tokens") > teto) return null;
                doc.put("tokens", doc.getLong("tokens") + ((Number) inc.get("tokens")).longValue());
                return new Document(doc);
            }
        });
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(LimiteTokensIaService.COLECAO))).thenAnswer(i -> {
            var filtro = ((Query) i.getArgument(0)).getQueryObject();
            var inc = ((Update) i.getArgument(1)).getUpdateObject().get("$inc", Document.class);
            synchronized (documentos) {
                var doc = documentos.get(filtro.getString("_id"));
                doc.put("tokens", doc.getLong("tokens") + ((Number) inc.get("tokens")).longValue());
            }
            return com.mongodb.client.result.UpdateResult.acknowledged(1, 1L, null);
        });
    }

    void usuario(long id) throws Exception {
        var usuario = new Usuario();
        var field = Usuario.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(usuario, id);
        when(atual.obter()).thenReturn(usuario);
    }

    @Test
    void reservaEntradaESaidaAjustaPeloUsoRealEBloqueiaExcesso() {
        var reserva = service.reservar(2000);
        assertThat(reserva.tokens()).isEqualTo(3000);
        assertThatThrownBy(() -> service.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
        service.concluir(reserva, 1200);
        assertThatCode(() -> service.reservar(800)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
    }

    @Test
    void renovaNoLimiteExatoDoPeriodo() {
        service.reservar(2000);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T12:59:59Z"));
        assertThatThrownBy(() -> service.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T13:00:00Z"));
        assertThatCode(() -> service.reservar(2000)).doesNotThrowAnyException();
    }

    @Test
    void isolaUsuariosEPreservaCotaAposRecriarServico() throws Exception {
        service.reservar(2000);
        var reiniciado = new LimiteTokensIaService(mongo, atual, 3000, 3600, 1000, clock);
        assertThatThrownBy(() -> reiniciado.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
        usuario(8L);
        assertThatCode(() -> reiniciado.reservar(2000)).doesNotThrowAnyException();
    }

    @Test
    void chamadasConcorrentesNaoGastamAMesmaCota() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var resultados = executor.invokeAll(IntStream.range(0, 20).<Callable<Boolean>>mapToObj(i -> () -> {
                try { service.reservar(500); return true; }
                catch (LimiteTokensIaException e) { return false; }
            }).toList());
            int aceitas = 0;
            for (var resultado : resultados) if (resultado.get()) aceitas++;
            assertThat(aceitas).isEqualTo(2);
        }
    }

    @Test
    void consumoRealMaiorQueEstimativaBloqueiaNovasChamadas() {
        service.concluir(service.reservar(500), 3500);
        assertThatThrownBy(() -> service.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
    }

    @Test
    void ajusteTardioAfetaAJanelaOriginal() {
        var anterior = service.reservar(2000);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-20T13:00:00Z"));
        service.reservar(2000);
        service.concluir(anterior, 500);
        assertThatThrownBy(() -> service.reservar(0)).isInstanceOf(LimiteTokensIaException.class);
    }

    @Test
    void entradaMuitoGrandeERecusadaAntesDeGravarNoBanco() {
        assertThatThrownBy(() -> service.reservar(Long.MAX_VALUE)).isInstanceOf(LimiteTokensIaException.class);
        verifyNoInteractions(mongo);
    }
}
