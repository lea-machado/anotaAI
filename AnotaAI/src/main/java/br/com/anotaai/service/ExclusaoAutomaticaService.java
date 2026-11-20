package br.com.anotaai.service;

import br.com.anotaai.repository.SolicitacaoExclusaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ExclusaoAutomaticaService {
    private static final Logger LOG = LoggerFactory.getLogger(ExclusaoAutomaticaService.class);
    private final SolicitacaoExclusaoRepository pedidos;
    private final AdminService adminService;
    private final GmailApiEmailService emailService;

    public ExclusaoAutomaticaService(SolicitacaoExclusaoRepository pedidos, AdminService adminService,
            GmailApiEmailService emailService) {
        this.pedidos = pedidos;
        this.adminService = adminService;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${anotaai.exclusao.intervalo-ms:60000}", initialDelay = 60000)
    public void excluirContasVencidas() {
        var limite = LocalDateTime.now().minusDays(SolicitacaoExclusaoService.PRAZO_DIAS);
        for (var pedido : pedidos.findBySolicitadaEmLessThanEqual(limite)) {
            try {
                adminService.excluirContaAutomatica(pedido);
                try {
                    emailService.enviarConfirmacaoExclusao(pedido.getEmail(), pedido.getNome());
                } catch (RuntimeException exception) {
                    LOG.warn("Conta excluída, mas não foi possível enviar a confirmação.", exception);
                }
            } catch (RuntimeException exception) {
                LOG.warn("Não foi possível concluir automaticamente o pedido de exclusão {}.", pedido.getId(), exception);
            }
        }
    }
}
