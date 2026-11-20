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
    @ExceptionHandler(br.com.anotaai.service.LimiteTokensIaException.class)
    org.springframework.http.ResponseEntity<Map<String, Object>> limiteTokens(
            br.com.anotaai.service.LimiteTokensIaException exception) {
        var segundos = Math.max(1, java.time.Duration.between(java.time.Instant.now(), exception.getReiniciaEm())
                .getSeconds() + 1);
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(segundos))
                .body(Map.of("codigo", "LIMITE_TOKENS_IA", "mensagem", exception.getMessage() + " Nova cota em "
                        + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
                                .withZone(java.time.ZoneId.of("America/Sao_Paulo"))
                                .format(exception.getReiniciaEm()) + " (horário de Brasília).",
                        "reiniciaEm", exception.getReiniciaEm().toString(), "momento", LocalDateTime.now()));
    }

    @ExceptionHandler({ org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.DuplicateKeyException.class })
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> conflito() {
        return erro(
                "Os dados foram alterados em outra solicitação ou já estão cadastrados. Atualize a página e tente novamente.");
    }

    @ExceptionHandler(br.com.anotaai.service.OpenRouterException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, Object> openRouterIndisponivel(br.com.anotaai.service.OpenRouterException exception) {
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

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> conflitoEstado(IllegalStateException exception) {
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
