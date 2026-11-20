package br.com.anotaai.service;

import java.time.Instant;

public class LimiteTokensIaException extends RuntimeException {
    private final Instant reiniciaEm;

    public LimiteTokensIaException(Instant reiniciaEm) {
        super("A cota de tokens disponível não é suficiente para esta solicitação. Aguarde a renovação ou reduza o texto.");
        this.reiniciaEm = reiniciaEm;
    }

    public Instant getReiniciaEm() { return reiniciaEm; }
}
