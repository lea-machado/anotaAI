package br.com.anotaai.controller;

import br.com.anotaai.service.RecursoNaoEncontradoException;
import br.com.anotaai.service.ServicoEmailException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.DuplicateKeyException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> conflito() {
        return erro("Os dados foram alterados em outra solicitação ou já estão cadastrados. Atualize a página e tente novamente.");
    }
    @ExceptionHandler(br.com.anotaai.service.GeminiException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, Object> geminiIndisponivel(br.com.anotaai.service.GeminiException exception) {
        return erro(exception.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> notFound(RecursoNaoEncontradoException exception) {
        return erro(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> badRequest(IllegalArgumentException exception) {
        return erro(exception.getMessage());
    }

    @ExceptionHandler(ServicoEmailException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, Object> emailIndisponivel(ServicoEmailException exception) {
        return erro(exception.getMessage());
    }

    private Map<String, Object> erro(String mensagem) {
        return Map.of("mensagem", mensagem, "momento", LocalDateTime.now());
    }
}
