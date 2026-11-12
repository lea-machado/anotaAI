package br.com.anotaai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "solicitacoes_exclusao")
public class SolicitacaoExclusao {
    @Id
    private Long id;

    private Long usuarioId;
    private String nome;
    private String email;
    private LocalDateTime solicitadaEm;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getSolicitadaEm() { return solicitadaEm; }
    public void setSolicitadaEm(LocalDateTime solicitadaEm) { this.solicitadaEm = solicitadaEm; }
}
