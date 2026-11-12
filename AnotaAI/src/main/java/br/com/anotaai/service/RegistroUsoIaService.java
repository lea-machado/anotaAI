package br.com.anotaai.service;

import br.com.anotaai.model.RegistroUsoIa;
import br.com.anotaai.repository.RegistroUsoIaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RegistroUsoIaService {
    private final RegistroUsoIaRepository repository;
    private final UsuarioAtualService usuarioAtualService;

    public RegistroUsoIaService(RegistroUsoIaRepository repository, UsuarioAtualService usuarioAtualService) {
        this.repository = repository;
        this.usuarioAtualService = usuarioAtualService;
    }

    public void registrar(String funcionalidade) {
        var registro = new RegistroUsoIa();
        registro.setUsuarioId(usuarioAtualService.obter().getId());
        registro.setFuncionalidade(funcionalidade);
        registro.setCriadoEm(LocalDateTime.now());
        repository.save(registro);
    }
}
