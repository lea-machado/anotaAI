package br.com.anotaai.config;

import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@DependsOn("mongoCollections")
public class IdentificadoresContaInicializador implements InitializingBean {
    private final MongoTemplate mongo;

    public IdentificadoresContaInicializador(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private Criteria semIdentificador() {
        return new Criteria().orOperator(Criteria.where("idPublico").exists(false),
                Criteria.where("idPublico").is(null), Criteria.where("idPublico").is(""));
    }

    @Override
    public void afterPropertiesSet() {
        // Apenas adiciona o identificador público. Chaves, referências e dados existentes são preservados.
        var query = Query.query(semIdentificador());
        query.fields().include("_id");
        for (var usuario : mongo.find(query, Document.class, "usuarios")) {
            var filtro = Query.query(new Criteria().andOperator(Criteria.where("_id").is(usuario.get("_id")),
                    semIdentificador()));
            mongo.updateFirst(filtro, new Update().set("idPublico", UUID.randomUUID().toString()), "usuarios");
        }
    }
}
