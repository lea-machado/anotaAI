package br.com.anotaai.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentificadoresContaInicializadorTest {
    MongoTemplate mongo = mock(MongoTemplate.class);

    @Test
    void adicionaUuidsDistintosSemAlterarChavesOuReferencias() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(List.of(new Document("_id", 7L), new Document("_id", 8L)));
        new IdentificadoresContaInicializador(mongo).afterPropertiesSet();
        var queries = ArgumentCaptor.forClass(Query.class);
        var updates = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(queries.capture(), updates.capture(), eq("usuarios"));
        var primeiro = updates.getAllValues().get(0).getUpdateObject().get("$set", Document.class);
        var segundo = updates.getAllValues().get(1).getUpdateObject().get("$set", Document.class);
        assertThat(primeiro).hasSize(1).containsKey("idPublico");
        assertThat(UUID.fromString(primeiro.getString("idPublico")).version()).isEqualTo(4);
        assertThat(primeiro.getString("idPublico")).isNotEqualTo(segundo.getString("idPublico"));
        assertThat(queries.getAllValues().get(0).getQueryObject().toJson()).contains("7", "idPublico", "$exists");
        verify(mongo, never()).remove(any(Query.class), anyString());
    }

    @Test
    void novaInicializacaoNaoTrocaIdentificadoresJaPersistidos() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(List.of(new Document("_id", 7L))).thenReturn(List.of());
        var inicializador = new IdentificadoresContaInicializador(mongo);
        inicializador.afterPropertiesSet();
        inicializador.afterPropertiesSet();
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq("usuarios"));
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(2)).find(query.capture(), eq(Document.class), eq("usuarios"));
        assertThat(query.getValue().getQueryObject().toJson()).contains("idPublico", "$exists", "$or");
    }
}
