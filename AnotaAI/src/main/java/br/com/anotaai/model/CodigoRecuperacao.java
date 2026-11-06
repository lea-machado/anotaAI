package br.com.anotaai.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "codigos_recuperacao")
public class CodigoRecuperacao {
    @Id
    private Long id;

    @Version
    @JsonIgnore
    private Long versao;

    @Indexed(unique = true)
    private UUID desafioId;

    @DBRef

    private Usuario usuario;

    private String codigoHash;

    private int tentativas;

    private LocalDateTime expiraEm;

    private LocalDateTime usadoEm;

    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public UUID getDesafioId() { return desafioId; }
    public void setDesafioId(UUID desafioId) { this.desafioId = desafioId; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }
    public int getTentativas() { return tentativas; }
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }
    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
