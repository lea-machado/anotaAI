package br.com.anotaai.config;

import br.com.anotaai.model.*;
import org.bson.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.util.ReflectionUtils;
import java.util.List;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    // Mantém os IDs existentes e os links da aplicação após a migração.
    @Bean
    BeforeConvertCallback<Object> numericIds(org.springframework.beans.factory.ObjectProvider<MongoTemplate> provider) {
        return (entity, collection) -> {
            var field = ReflectionUtils.findField(entity.getClass(), "id");
            if (field != null && field.getType() == Long.class) {
                ReflectionUtils.makeAccessible(field);
                if (ReflectionUtils.getField(field, entity) == null) {
                    var counter = provider.getObject().findAndModify(Query.query(Criteria.where("_id").is(collection)),
                            new Update().inc("value", 1L), FindAndModifyOptions.options().upsert(true).returnNew(true),
                            Document.class, "sequencias");
                    ReflectionUtils.setField(field, entity, ((Number) counter.get("value")).longValue());
                }
            }
            return entity;
        };
    }

    @Bean
    InitializingBean mongoCollections(MongoTemplate mongo) {
        return () -> {
            for (var type : List.of(Usuario.class, Anotacao.class, Baralho.class, VersaoAnotacao.class,
                    CodigoAutenticacao.class, CodigoRecuperacao.class, RegistroAuditoria.class, SolicitacaoExclusao.class,
                    RegistroUsoIa.class)) {
                if (!mongo.collectionExists(type)) mongo.createCollection(type);
            }
            mongo.updateMulti(new Query(), new Update().unset("email").unset("detalhes")
                    .unset("enderecoIp").unset("userAgent"), RegistroAuditoria.class);
            if (!mongo.collectionExists("sequencias")) mongo.createCollection("sequencias");
            for (var name : List.of("anotacoes", "baralhos", "codigos_autenticacao", "codigos_recuperacao", "registros_auditoria")) {
                mongo.indexOps(name).ensureIndex(new Index().on("usuario.$id", Sort.Direction.ASC)
                        .on(name.equals("anotacoes") ? "atualizadoEm" : "criadoEm", Sort.Direction.DESC));
            }
            mongo.indexOps(VersaoAnotacao.class).ensureIndex(new Index().on("anotacao.$id", Sort.Direction.ASC)
                    .on("criadoEm", Sort.Direction.ASC));
        };
    }
}
