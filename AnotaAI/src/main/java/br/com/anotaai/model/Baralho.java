package br.com.anotaai.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.CreatedDate;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "baralhos")
public class Baralho {
    @Id
    private Long id;

    @Version
    @JsonIgnore
    private Long versao;
    @DBRef

    private Usuario usuario;

    private String titulo;

    @CreatedDate
    private LocalDateTime criadoEm;

    private List<Flashcard> flashcards = new ArrayList<>();
    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public List<Flashcard> getFlashcards() { return flashcards; }
}
