package br.com.anotaai.service;

import br.com.anotaai.model.DocumentoLegal;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.DocumentoLegalRepository;
import br.com.anotaai.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TermosServiceTest {
    private final DocumentoLegalRepository documentos = mock(DocumentoLegalRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final TermosService service = new TermosService(documentos, usuarios);
    private final List<DocumentoLegal> registrados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        when(documentos.save(any())).thenAnswer(invocation -> {
            DocumentoLegal documento = invocation.getArgument(0);
            registrados.add(documento);
            return documento;
        });
        when(documentos.findTopByTipoOrderByNumeroDesc(any())).thenAnswer(invocation -> {
            String tipo = invocation.getArgument(0);
            return registrados.stream().filter(documento -> tipo.equals(documento.getTipo()))
                    .max(Comparator.comparingInt(DocumentoLegal::getNumero));
        });
        when(documentos.findByTipoOrderByNumeroDesc(any())).thenAnswer(invocation -> {
            String tipo = invocation.getArgument(0);
            return registrados.stream().filter(documento -> tipo.equals(documento.getTipo()))
                    .sorted(Comparator.comparingInt(DocumentoLegal::getNumero).reversed()).toList();
        });
        when(documentos.findById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return registrados.stream().filter(documento -> id.equals(documento.getId())).findFirst();
        });
    }

    @Test
    void iniciaComUmUnicoDocumentoOriginal() {
        var documento = service.atual();
        assertThat(documento.getVersao()).isEqualTo(TermosService.VERSAO_INICIAL);
        assertThat(documento.getTipo()).isEqualTo(TermosService.UNIFICADO);
        assertThat(documento.getFormato()).isEqualTo("HTML");
        assertThat(documento.getConteudo()).contains("AnotaAI", "LGPD");
        assertThat(registrados).hasSize(1);
        assertThat(service.historico()).containsExactly(documento);
    }

    @Test
    void cadastroAnteriorMantemAceiteEnquantoNaoHouverNovaVersao() {
        var usuario = new Usuario();
        usuario.setVersaoTermos(TermosService.VERSAO_INICIAL);
        usuario.setTermosAceitosEm(LocalDateTime.now().minusDays(10));

        assertThat(service.aceitouVigentes(usuario)).isTrue();
        assertThat(service.status(usuario)).containsEntry("precisaAceitar", false)
                .containsEntry("aceitou", true)
                .containsEntry("versao", TermosService.VERSAO_INICIAL);
    }

    @Test
    void publicarGeraNovaVersaoEHistoricoSemSobrescreverAnterior() {
        var original = service.atual();
        var texto = "Novos Termos de Uso e Política de Privacidade dos estudantes. ".repeat(3);
        var novo = service.publicar(texto, "admin@example.com");
        assertThat(novo.getNumero()).isEqualTo(2);
        assertThat(novo.getVersao()).isEqualTo("v2");
        assertThat(novo.getTipo()).isEqualTo(TermosService.UNIFICADO);
        assertThat(novo.getFormato()).isEqualTo("TEXTO");
        assertThat(service.historico()).containsExactly(novo, original);
        assertThat(service.versao(original.getId())).isEqualTo(original);
        assertThatThrownBy(() -> service.versao("inexistente"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void novaVersaoBloqueiaAteAceiteUnico() {
        var usuario = new Usuario();
        usuario.setVersaoTermos(TermosService.VERSAO_INICIAL);
        assertThat(service.aceitouVigentes(usuario)).isTrue();
        service.publicar("Alteração conjunta dos termos e da política de privacidade. ".repeat(3),
                "admin@example.com");
        assertThat(service.aceitouVigentes(usuario)).isFalse();
        assertThat(service.status(usuario)).containsEntry("precisaAceitar", true)
                .containsEntry("aceitou", false);
        assertThatThrownBy(() -> service.aceitar(usuario, true, TermosService.VERSAO_INICIAL))
                .hasMessageContaining("vigente");
        service.aceitar(usuario, true, "v2");
        assertThat(service.aceitouVigentes(usuario)).isTrue();
        assertThat(usuario.getTermosAceitosEm()).isNotNull();
        verify(usuarios).save(usuario);
    }

    @Test
    void importarDocumentosAntigosIguaisNaoDuplicaNemExigeNovoAceite() {
        registradoAntigo("TERMOS", 1, TermosService.VERSAO_INICIAL, "Documento anterior unificado.", "TEXTO");
        registradoAntigo("PRIVACIDADE", 1, TermosService.VERSAO_INICIAL, "Documento anterior unificado.", "TEXTO");
        var documento = service.atual();
        assertThat(documento.getVersao()).isEqualTo(TermosService.VERSAO_INICIAL);
        assertThat(documento.getConteudo()).isEqualTo("Documento anterior unificado.");
        assertThat(service.historico()).containsExactly(documento);
        assertThat(registrados.stream().filter(d -> TermosService.UNIFICADO.equals(d.getTipo())).count())
                .isEqualTo(1);
    }

    @Test
    void importarVersoesAntigasDiferentesMantemAmbosConteudosEExigeAceite() {
        registradoAntigo("TERMOS", 2, "v2", "Conteúdo de termos alterado", "TEXTO");
        registradoAntigo("PRIVACIDADE", 1, TermosService.VERSAO_INICIAL, "<p>Texto de privacidade</p>", "HTML");
        var documento = service.atual();
        assertThat(documento.getVersao()).isEqualTo("v3");
        assertThat(documento.getConteudo()).contains("Conteúdo de termos alterado", "Texto de privacidade");
        assertThat(documento.getFormato()).isEqualTo("HTML");
        var usuario = new Usuario();
        usuario.setVersaoTermos("v2");
        assertThat(service.aceitouVigentes(usuario)).isFalse();
    }

    @Test
    void naoPermiteDocumentosCurtosNemRepublicarMesmoTexto() {
        assertThatThrownBy(() -> service.publicar("curto", "admin@example.com"))
                .hasMessageContaining("100");
        var texto = "Novos Termos de Uso e Política de Privacidade. ".repeat(4);
        service.publicar(texto, "admin@example.com");
        assertThatThrownBy(() -> service.publicar(texto, "admin@example.com"))
                .hasMessageContaining("diferente");
    }

    @Test
    void versaoAntigaIsoladaNaoFazParteDoHistoricoUnificado() {
        var antigo = registradoAntigo("TERMOS", 1, TermosService.VERSAO_INICIAL, "texto", "TEXTO");
        service.atual();
        assertThatThrownBy(() -> service.versao(antigo.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private DocumentoLegal registradoAntigo(String tipo, int numero, String versao, String conteudo, String formato) {
        var documento = new DocumentoLegal();
        documento.setId(java.util.UUID.randomUUID().toString());
        documento.setTipo(tipo);
        documento.setNumero(numero);
        documento.setVersao(versao);
        documento.setConteudo(conteudo);
        documento.setFormato(formato);
        documento.setPublicadoEm(LocalDateTime.now().minusDays(10));
        registrados.add(documento);
        return documento;
    }
}