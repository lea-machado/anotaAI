package br.com.anotaai.model;

public class Flashcard {

    private String pergunta;

    private String resposta;

    protected Flashcard() {}
    public Flashcard(String pergunta, String resposta) {
        this.pergunta = pergunta;
        this.resposta = resposta;
    }
    public String getPergunta() { return pergunta; }
    public String getResposta() { return resposta; }
}
