package br.com.anotaai.service;

import br.com.anotaai.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.bson.Document;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MongoMigratedDataIT {
    @Autowired UsuarioRepository usuarios;
    @Autowired AnotacaoRepository notas;
    @Autowired BaralhoRepository baralhos;
    @Autowired RegistroAuditoriaRepository auditoria;
    @Autowired AnotacaoService anotacoes;
    @Autowired FlashcardService flashcards;
    @Autowired DesempenhoService desempenho;
    @Autowired MongoTemplate mongo;

    @Test void usuariosEConteudoMigradoPodemSerConsultadosPelaAplicacao() {
        org.junit.jupiter.api.Assertions.assertTrue(false);
        assertFalse(usuarios.findAll().isEmpty());
        try {
            for (var usuario : usuarios.findAll()) {
                assertNotNull(usuario.getCriadoEm());
                assertNotNull(usuario.getSenha());
                assertTrue(usuario.getSenha().startsWith("$2"));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario.getEmail(), "", List.of()));
                List<br.com.anotaai.model.Anotacao> userNotes = anotacoes.listar(null);
                assertEquals(userNotes.size(), ((Number) desempenho.obter().get("anotacoes")).intValue());
                for (var note : userNotes) {
                    assertNotNull(note.getAtualizadoEm());
                    assertEquals(usuario.getId(), note.getUsuario().getId());
                    assertFalse(anotacoes.listarVersoes(note.getId()).isEmpty());
                }
                for (var deck : flashcards.listar()) {
                    assertEquals(deck.quantidade(), flashcards.buscar(deck.id()).flashcards().size());
                }
                auditoria.findTop50ByUsuarioIdOrderByCriadoEmDesc(usuario.getId());
            }
            for (var collection : List.of("usuarios", "anotacoes", "versoes_anotacao", "baralhos",
                    "codigos_autenticacao", "codigos_recuperacao", "registros_auditoria")) {
                var largest = mongo.getCollection(collection).find().sort(new Document("_id", -1)).first();
                if (largest == null) continue;
                var counter = mongo.getCollection("sequencias").find(new Document("_id", collection)).first();
                assertNotNull(counter);
                assertTrue(((Number) counter.get("value")).longValue() >= ((Number) largest.get("_id")).longValue());
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
}