package br.com.anotaai.service;

import br.com.anotaai.model.DocumentoLegal;
import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.DocumentoLegalRepository;
import br.com.anotaai.repository.UsuarioRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TermosService {
    public static final String VERSAO_INICIAL = "2026-09-25";
    public static final String UNIFICADO = "UNIFICADO";
    private final DocumentoLegalRepository documentos;
    private final UsuarioRepository usuarios;

    public TermosService(DocumentoLegalRepository documentos, UsuarioRepository usuarios) {
        this.documentos = documentos;
        this.usuarios = usuarios;
    }

    public DocumentoLegal atual() {
        return documentos.findTopByTipoOrderByNumeroDesc(UNIFICADO).orElseGet(this::inicializar);
    }

    private DocumentoLegal inicializar() {
        var termos = documentos.findTopByTipoOrderByNumeroDesc("TERMOS").orElse(null);
        var privacidade = documentos.findTopByTipoOrderByNumeroDesc("PRIVACIDADE").orElse(null);
        var documento = new DocumentoLegal();
        documento.setId(UUID.randomUUID().toString());
        documento.setTipo(UNIFICADO);
        var ambosOriginais = (termos == null || termos.getNumero() == 1)
                && (privacidade == null || privacidade.getNumero() == 1)
                && (termos == null || privacidade == null
                        || Objects.equals(termos.getConteudo(), privacidade.getConteudo()));
        var maiorNumero = Math.max(termos == null ? 1 : termos.getNumero(),
                privacidade == null ? 1 : privacidade.getNumero());
        documento.setNumero(ambosOriginais ? 1 : maiorNumero + 1);
        documento.setVersao(documento.getNumero() == 1 ? VERSAO_INICIAL : "v" + documento.getNumero());
        if (termos == null && privacidade == null) {
            documento.setConteudo(conteudoInicial());
            documento.setFormato("HTML");
        } else if (termos == null || privacidade == null
                || Objects.equals(termos.getConteudo(), privacidade.getConteudo())) {
            var anterior = termos == null ? privacidade : termos;
            documento.setConteudo(anterior.getConteudo());
            documento.setFormato(anterior.getFormato());
        } else {
            documento.setConteudo("<h2 class=\"legal-heading\">Termos de Uso</h2>"
                    + converterParaHtml(termos) + "<h2 class=\"legal-heading\">Política de Privacidade</h2>"
                    + converterParaHtml(privacidade));
            documento.setFormato("HTML");
        }
        documento.setPublicadoEm(documento.getNumero() == 1
                ? LocalDateTime.of(2026, 9, 25, 0, 0) : LocalDateTime.now());
        documento.setPublicadoPor("Sistema");
        try {
            return documentos.save(documento);
        } catch (DuplicateKeyException exception) {
            return documentos.findTopByTipoOrderByNumeroDesc(UNIFICADO)
                    .orElseThrow(() -> new IllegalStateException("Não foi possível consultar o documento legal."));
        }
    }

    private String converterParaHtml(DocumentoLegal documento) {
        if ("HTML".equals(documento.getFormato())) {
            return documento.getConteudo();
        }
        return "<div class=\"legal-text-content\">" + documento.getConteudo()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;") + "</div>";
    }

    private String conteudoInicial() {
        try (var stream = new ClassPathResource("static/pages/privacidade.html").getInputStream()) {
            var pagina = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var inicio = pagina.indexOf("<div class=\"legal-document\"");
            if (inicio < 0) throw new IllegalStateException("Documento legal inicial não encontrado.");
            inicio = pagina.indexOf('>', inicio) + 1;
            var fim = pagina.indexOf("</div>", inicio);
            if (fim < 0) throw new IllegalStateException("Documento legal inicial inválido.");
            return pagina.substring(inicio, fim).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível carregar os termos iniciais.", exception);
        }
    }

    public List<DocumentoLegal> historico() {
        atual();
        return documentos.findByTipoOrderByNumeroDesc(UNIFICADO);
    }

    public DocumentoLegal versao(String id) {
        var documento = documentos.findById(id).orElseThrow(
                () -> new RecursoNaoEncontradoException("Versão dos termos não encontrada."));
        if (!UNIFICADO.equals(documento.getTipo())) {
            throw new RecursoNaoEncontradoException("Versão dos termos não encontrada.");
        }
        return documento;
    }

    public DocumentoLegal publicar(String conteudo, String emailAdministrador) {
        if (conteudo == null || conteudo.strip().length() < 100 || conteudo.length() > 50000) {
            throw new IllegalArgumentException("O texto deve conter entre 100 e 50.000 caracteres.");
        }
        var anterior = atual();
        if ("TEXTO".equals(anterior.getFormato()) && anterior.getConteudo().equals(conteudo.strip())) {
            throw new IllegalArgumentException("O novo texto deve ser diferente da versão atual.");
        }
        var documento = new DocumentoLegal();
        documento.setId(UUID.randomUUID().toString());
        documento.setTipo(UNIFICADO);
        documento.setNumero(anterior.getNumero() + 1);
        documento.setVersao("v" + documento.getNumero());
        documento.setConteudo(conteudo.strip());
        documento.setFormato("TEXTO");
        documento.setPublicadoEm(LocalDateTime.now());
        documento.setPublicadoPor(emailAdministrador);
        return documentos.save(documento);
    }

    public boolean aceitouVigentes(Usuario usuario) {
        return Objects.equals(usuario.getVersaoTermos(), atual().getVersao());
    }

    public void validarCadastro(Boolean aceitou, String versao) {
        if (!Boolean.TRUE.equals(aceitou) || !Objects.equals(versao, atual().getVersao())) {
            throw new IllegalArgumentException("É necessário aceitar a versão vigente dos Termos de Uso e Política de Privacidade.");
        }
    }

    public Map<String, Object> status(Usuario usuario) {
        var documento = atual();
        var aceitou = Objects.equals(usuario.getVersaoTermos(), documento.getVersao());
        return Map.of("precisaAceitar", !aceitou, "aceitou", aceitou, "versao", documento.getVersao());
    }

    public Map<String, Object> aceitar(Usuario usuario, Boolean aceitou, String versao) {
        validarCadastro(aceitou, versao);
        usuario.setVersaoTermos(versao);
        usuario.setTermosAceitosEm(LocalDateTime.now());
        usuarios.save(usuario);
        return status(usuario);
    }
}
