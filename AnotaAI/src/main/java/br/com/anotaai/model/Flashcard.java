package br.com.anotaai.model;

public class Flashcard {

    private String pergunta;

    private String resposta;
    private int acertos;
    private int erros;
    private boolean pendenteRevisao;

    protected Flashcard() {}
    public Flashcard(String pergunta, String resposta) {
        this.pergunta = pergunta;
        this.resposta = resposta;
    }
    public String getPergunta() { return pergunta; }
    public String getResposta() { return resposta; }
    public int getAcertos() { return acertos; }
    public int getErros() { return erros; }
    public boolean isPendenteRevisao() { return pendenteRevisao; }
    public void registrarResultado(boolean correto) {
        if (correto) { acertos++; pendenteRevisao = false; }
        else { erros++; pendenteRevisao = true; }
    }
}
