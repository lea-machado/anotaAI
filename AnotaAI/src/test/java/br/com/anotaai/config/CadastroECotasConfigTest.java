package br.com.anotaai.config;

import br.com.anotaai.service.GmailApiEmailService;
import br.com.anotaai.service.LimiteTokensIaService;
import br.com.anotaai.service.UsuarioAtualService;
import br.com.anotaai.service.UsuarioService;
import br.com.anotaai.service.VerificacaoCadastroService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CadastroECotasConfigTest {
    @Test
    void iniciaServicosComConfiguracaoPadraoSemVariaveisDoRender() throws Exception {
        var properties = new ResourcePropertySource(new ClassPathResource("application.properties"));
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(properties))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
                .withBean(UsuarioAtualService.class, () -> mock(UsuarioAtualService.class))
                .withBean(UsuarioService.class, () -> mock(UsuarioService.class))
                .withBean(GmailApiEmailService.class, () -> mock(GmailApiEmailService.class))
                .withBean(LimiteTokensIaService.class)
                .withBean(VerificacaoCadastroService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(LimiteTokensIaService.class)
                            .hasSingleBean(VerificacaoCadastroService.class);
                    assertThat(context.getBean(LimiteTokensIaService.class).maxTokensSaida()).isEqualTo(4096);
                });
    }
}
