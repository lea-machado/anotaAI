package br.com.anotaai.service;

public class ServicoEmailException extends RuntimeException {
    public ServicoEmailException(String mensagem) {
        super(mensagem);
    }

    public ServicoEmailException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
