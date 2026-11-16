package br.com.anotaai.model;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class Flashcard {

    private String pergunta;

    private String resposta;
    private List<String> distratores = List.of();
    private int acertos;
    private int erros;
    private boolean pendenteRevisao;

    protected Flashcard() {
    }

    public Flashcard(String pergunta, String resposta) {
        this(pergunta, resposta, null);
    }

    public Flashcard(String pergunta, String resposta, List<String> distratores) {
        this.pergunta = pergunta;
        this.resposta = resposta;
        if (distratores != null && !distratores.isEmpty()) {
            this.distratores = validarDistratores(resposta, distratores);
        }
    }

    public String getPergunta() {
        return pergunta;
    }

    public String getResposta() {
        return resposta;
    }

    public List<String> getDistratores() {
        return distratores == null ? List.of() : distratores;
    }

    public static List<String> validarDistratores(String resposta, List<String> distratores) {
        if (distratores.size() != 3) {
            throw new IllegalArgumentException("O flashcard deve ter três alternativas incorretas.");
        }
        var textos = new HashSet<String>();
        textos.add(resposta.strip().toLowerCase(Locale.ROOT));
        for (String alternativa : distratores) {
            if (alternativa == null || alternativa.isBlank() || alternativa.length() > 3000
                    || !textos.add(alternativa.strip().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("As alternativas devem ser preenchidas e diferentes entre si e da resposta.");
            }
        }
        return distratores.stream().map(String::strip).toList();
    }

    public int getAcertos() {
        return acertos;
    }

    public int getErros() {
        return erros;
    }

    public boolean isPendenteRevisao() {
        return pendenteRevisao;
    }

    public void registrarResultado(boolean correto) {
        if (correto) {
            acertos++;
            pendenteRevisao = false;
        } else {
            erros++;
            pendenteRevisao = true;
        }
    }
}
