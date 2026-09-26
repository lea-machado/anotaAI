package br.com.anotaai.controller;

import br.com.anotaai.service.DesempenhoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/desempenho")
public class DesempenhoController {
    private final DesempenhoService desempenhoService;

    public DesempenhoController(DesempenhoService desempenhoService) {
        this.desempenhoService = desempenhoService;
    }

    @GetMapping
    public Map<String, Object> obter() {
        return desempenhoService.obter();
    }
}
