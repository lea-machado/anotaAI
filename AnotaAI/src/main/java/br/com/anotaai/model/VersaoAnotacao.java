package br.com.anotaai.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.CreatedDate;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Document(collection = "versoes_anotacao")
public class VersaoAnotacao {
    @Id
    private Long id;

    @Version
    @JsonIgnore
    private Long versao;

    @DBRef

    private Anotacao anotacao;

    private String tipo;

    private String conteudo;

    @CreatedDate
    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public Anotacao getAnotacao() { return anotacao; }
    public void setAnotacao(Anotacao anotacao) { this.anotacao = anotacao; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
