package br.com.anotaai.service;

import br.com.anotaai.model.*;
import br.com.anotaai.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Executar explicitamente com -Dtest=MongoPersistenceIT. Usa banco isolado e o remove ao terminar. */
@SpringBootTest
class MongoPersistenceIT {
    static final String DATABASE = "anotaai_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }
    @Autowired MongoTemplate mongo;
    @Autowired UsuarioRepository usuarios;
    @Autowired AnotacaoRepository notas;
    @Autowired VersaoAnotacaoRepository versoes;
    @Autowired CodigoRecuperacaoRepository codigos;
    @Autowired BaralhoRepository baralhos;
    @Autowired AnotacaoService service;
    @Autowired PlatformTransactionManager transactions;
    @MockBean UsuarioAtualService atual;
    Usuario usuario;

    @BeforeEach void owner() {
        usuario = new Usuario();
        usuario.setNome("Teste de persistência");
        usuario.setEmail(UUID.randomUUID() + "@example.invalid");
        usuario.setSenha("hash-ficticio");
        usuario.setPerfil("USER");
        usuario = usuarios.save(usuario);
        when(atual.obter()).thenReturn(usuario);
        assertNotNull(usuario.getId());
        assertNotNull(usuario.getCriadoEm());
    }

    @AfterAll static void cleanup(@Autowired MongoTemplate mongo) {
        if (!mongo.getDb().getName().equals(DATABASE) || !DATABASE.startsWith("anotaai_test_"))
            throw new IllegalStateException("Banco de teste inesperado");
        mongo.getDb().drop();
    }

    Anotacao data(String content) {
        var n = new Anotacao(); n.setTitulo("Biologia"); n.setConteudo(content); return n;
    }

    @Test void preservaReferenciasHistoricoAtualizacaoEExclusao() {
        var created = service.criar(data("Resumo"), "Resumo", "Original");
        assertNotNull(created.getAtualizadoEm());
        assertEquals(1, notas.findByUsuarioIdOrderByAtualizadoEmDesc(usuario.getId()).size());
        assertEquals(1, notas.findByUsuarioIdAndTituloContainingIgnoreCaseOrderByAtualizadoEmDesc(usuario.getId(), "BIO").size());
        assertEquals(1, notas.countByUsuarioId(usuario.getId()));
        assertEquals(usuario.getEmail(), notas.findById(created.getId()).orElseThrow().getUsuario().getEmail());
        assertTrue(notas.findByIdAndUsuarioId(created.getId(), -100L).isEmpty());
        assertEquals(2, service.listarVersoes(created.getId()).size());
        service.atualizar(created.getId(), data("Editado"), null);
        assertEquals("Editado", notas.findById(created.getId()).orElseThrow().getConteudo());
        assertEquals(3, service.listarVersoes(created.getId()).size());
        service.excluir(created.getId());
        assertFalse(notas.existsById(created.getId()));
        assertTrue(versoes.findByAnotacaoIdOrderByCriadoEmAsc(created.getId()).isEmpty());
    }

    @Test void rollbackNaoDeixaAnotacaoOuVersaoParcial() {
        long beforeNotes = notas.count(), beforeVersions = versoes.count();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(status -> {
            service.criar(data("Teste rollback"), "Resumo", "Original");
            throw new IllegalStateException("Reverter teste");
        }));
        assertEquals(beforeNotes, notas.count()); assertEquals(beforeVersions, versoes.count());
    }

    @Test void codigosUuidCartoesEConcorrencia() {
        var c = new CodigoRecuperacao(); c.setDesafioId(UUID.randomUUID()); c.setUsuario(usuario);
        c.setCodigoHash("hash"); c.setCriadoEm(LocalDateTime.now()); c.setExpiraEm(LocalDateTime.now().plusMinutes(10));
        codigos.save(c);
        assertEquals(usuario.getId(), codigos.findByDesafioId(c.getDesafioId()).orElseThrow().getUsuario().getId());
        assertEquals(1, codigos.findByUsuarioIdAndUsadoEmIsNull(usuario.getId()).size());
        assertTrue(codigos.findTopByUsuarioIdOrderByCriadoEmDesc(usuario.getId()).isPresent());
        var first = codigos.findById(c.getId()).orElseThrow();
        var stale = codigos.findById(c.getId()).orElseThrow();
        first.setUsadoEm(LocalDateTime.now()); codigos.save(first);
        stale.setTentativas(1);
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> codigos.save(stale));
        var b = new Baralho(); b.setUsuario(usuario); b.setTitulo("Biologia");
        b.getFlashcards().add(new Flashcard("Primeira", "Resposta 1"));
        b.getFlashcards().add(new Flashcard("Segunda", "Resposta 2"));
        baralhos.save(b);
        assertEquals("Segunda", baralhos.findByIdAndUsuarioId(b.getId(), usuario.getId()).orElseThrow().getFlashcards().get(1).getPergunta());
        var duplicate = new Usuario(); duplicate.setEmail(usuario.getEmail());
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> usuarios.save(duplicate));
    }
}
