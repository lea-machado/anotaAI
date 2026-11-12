package br.com.anotaai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "registros_uso_ia")
public class RegistroUsoIa {
    @Id
    private Long id;
    private Long usuarioId;
    private String funcionalidade;
    private LocalDateTime criadoEm;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getFuncionalidade() { return funcionalidade; }
    public void setFuncionalidade(String funcionalidade) { this.funcionalidade = funcionalidade; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
