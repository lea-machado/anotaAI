package br.com.anotaai.model;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "documentos_legais")
@CompoundIndex(name = "tipo_numero_unico", def = "{'tipo': 1, 'numero': 1}", unique = true)
public class DocumentoLegal {
    @Id
    private String id;
    private String tipo;
    private int numero;
    private String versao;
    private String conteudo;
    private String formato;
    private LocalDateTime publicadoEm;
    private String publicadoPor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public String getVersao() { return versao; }
    public void setVersao(String versao) { this.versao = versao; }
    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
    public String getFormato() { return formato; }
    public void setFormato(String formato) { this.formato = formato; }
    public LocalDateTime getPublicadoEm() { return publicadoEm; }
    public void setPublicadoEm(LocalDateTime publicadoEm) { this.publicadoEm = publicadoEm; }
    @JsonIgnore
    public String getPublicadoPor() { return publicadoPor; }
    public void setPublicadoPor(String publicadoPor) { this.publicadoPor = publicadoPor; }
}
