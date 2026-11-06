package br.com.anotaai.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "usuarios")
public class Usuario {
    @Id
    private Long id;

    @Version
    @JsonIgnore
    private Long versao;

    private String nome;

    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    private String senha;

    private String perfil;

    /** Null keeps accounts migrated from PostgreSQL active by default. */
    private Boolean ativo = true;

    @CreatedDate
    private LocalDateTime criadoEm;

    private LocalDate dataNascimento;

    private String escolaridade;

    private LocalDateTime termosAceitosEm;

    private LocalDateTime maioridadeDeclaradaEm;

    private String versaoTermos;

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
    public Boolean getAtivo() { return ativo; }
    public boolean isAtivo() { return !Boolean.FALSE.equals(ativo); }
    public void setAtivo(Boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public String getEscolaridade() { return escolaridade; }
    public void setEscolaridade(String escolaridade) { this.escolaridade = escolaridade; }
    public LocalDateTime getTermosAceitosEm() { return termosAceitosEm; }
    public void setTermosAceitosEm(LocalDateTime termosAceitosEm) { this.termosAceitosEm = termosAceitosEm; }
    public LocalDateTime getMaioridadeDeclaradaEm() { return maioridadeDeclaradaEm; }
    public void setMaioridadeDeclaradaEm(LocalDateTime maioridadeDeclaradaEm) { this.maioridadeDeclaradaEm = maioridadeDeclaradaEm; }
    public String getVersaoTermos() { return versaoTermos; }
    public void setVersaoTermos(String versaoTermos) { this.versaoTermos = versaoTermos; }
}
