package br.com.anotaai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String senha;

    @Column(nullable = false, length = 20)
    private String perfil;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(length = 40)
    private String escolaridade;

    @Column(name = "termos_aceitos_em")
    private LocalDateTime termosAceitosEm;

    @Column(name = "maioridade_declarada_em")
    private LocalDateTime maioridadeDeclaradaEm;

    @Column(name = "versao_termos", length = 20)
    private String versaoTermos;

    @PrePersist
    void prePersist() {
        criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }
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
