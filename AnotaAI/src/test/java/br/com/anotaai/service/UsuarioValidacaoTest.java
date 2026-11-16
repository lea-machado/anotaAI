package br.com.anotaai.service;

import br.com.anotaai.model.Usuario;
import br.com.anotaai.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioValidacaoTest {
    final UsuarioRepository repo = mock(UsuarioRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final UsuarioService service = new UsuarioService(repo,encoder);
    Usuario u;
    @BeforeEach void preparar() {
        u = new Usuario(); org.springframework.test.util.ReflectionTestUtils.setField(u,"id",1L); u.setSenha("hash");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"", " ", "ab"})
    void rejeitaNomeAusenteOuCurto(String nome) { assertThatThrownBy(() -> service.validarNome(nome)).isInstanceOf(IllegalArgumentException.class); }
    @Test void validaLimitesDoNome() {
        assertThatThrownBy(() -> service.validarNome("a".repeat(121))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.validarNome(" Ana ")).isEqualTo("Ana"); assertThat(service.validarNome("a".repeat(120))).hasSize(120);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"", " ", "semarroba", "a@b", "a b@example.com"})
    void rejeitaEmailInvalido(String email) { assertThatThrownBy(() -> UsuarioService.validarEmail(email)).isInstanceOf(IllegalArgumentException.class); }
    @Test void normalizaEmailERejeitaComprimentoExcessivo() {
        assertThat(UsuarioService.validarEmail(" ANA@EXAMPLE.COM ")).isEqualTo("ana@example.com");
        assertThatThrownBy(() -> UsuarioService.validarEmail("a".repeat(180)+"@example.com")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"curta", "conta!forte2026", "CONTA!FORTE2026", "Conta!ForteLonga", "ContaForte2026", "Conta Forte2026", "Abcd!Forte2026", "Dcba!Forte2026", "Conta!1234Longa", "Conta!4321Longa", "Password!Longa8", "Senha!Longa2026", "Admin!Longa2026", "Qwer!Longa2026", "Rewq!Longa2026"})
    void rejeitaSenhasFracasESequencias(String senha) { assertThatThrownBy(() -> UsuarioService.validarSenha(senha)).isInstanceOf(IllegalArgumentException.class); }
    @Test void limiteDaSenhaConsideraBytesUtf8() {
        assertThatThrownBy(() -> UsuarioService.validarSenha("Á".repeat(36)+"a8!")).hasMessageContaining("72 bytes");
        assertThatCode(() -> UsuarioService.validarSenha("Conta!Forte2026")).doesNotThrowAnyException();
        assertThatCode(() -> UsuarioService.validarSenha("Conta!Forte2026🙂")).doesNotThrowAnyException();
    }
    @Test void aceiteExigeVersaoAtual() {
        for (var aceite : Arrays.asList(null,false,true)) {
            assertThatThrownBy(() -> service.criarUsuarioComum("Pessoa","p@example.com","Conta!Forte2026",LocalDate.now().minusYears(20),"Mestrado",aceite,true,"antiga"))
                    .hasMessageContaining("Termos");
        }
    }
    Usuario atualizar(String atual, String nova) { return service.atualizarPerfil(u," Pessoa "," P@EXAMPLE.COM ",null,atual,nova); }
    @Test void atualizaPerfilSemTrocarSenha() {
        assertThat(atualizar(null,null)).isSameAs(u); assertThat(u.getNome()).isEqualTo("Pessoa");
        assertThat(u.getEscolaridade()).isNull();
        assertThat(u.getEmail()).isEqualTo("p@example.com"); assertThat(u.getSenha()).isEqualTo("hash");
        atualizar(" "," "); when(repo.findByEmailIgnoreCase("p@example.com")).thenReturn(Optional.of(u));
        atualizar(null,""); verifyNoInteractions(encoder);
    }
    @Test void atualizaEscolaridadeSemExigirSenhaEConservaQuandoOmitida() {
        u.setEscolaridade("Ensino Médio Completo");
        assertThat(atualizar(null, null).getEscolaridade()).isEqualTo("Ensino Médio Completo");
        assertThat(service.atualizarPerfil(u, "Pessoa", "p@example.com", " Mestrado ", null, null).getEscolaridade())
                .isEqualTo("Mestrado");
        verify(repo, times(2)).save(u);
        verifyNoInteractions(encoder);
    }
    @Test void rejeitaEscolaridadeInvalidaAoAtualizarPerfil() {
        u.setEscolaridade("Ensino Médio Completo");
        assertThatThrownBy(() -> service.atualizarPerfil(u, "Pessoa", "p@example.com", "Outro nível", null, null))
                .hasMessage("Informe uma escolaridade válida.");
        assertThat(u.getEscolaridade()).isEqualTo("Ensino Médio Completo");
        verify(repo, never()).save(any());
    }
    @Test void emailDeOutraContaNaoPodeSerUsado() {
        var outro = new Usuario(); org.springframework.test.util.ReflectionTestUtils.setField(outro,"id",2L);
        when(repo.findByEmailIgnoreCase("p@example.com")).thenReturn(Optional.of(outro));
        assertThatThrownBy(() -> atualizar(null,null)).hasMessageContaining("Ja existe"); verify(repo,never()).save(any());
    }
    @Test void trocaDeSenhaExigeSenhaAtualCorretaENovaSenhaForte() {
        assertThatThrownBy(() -> atualizar(null,"Conta!Forte2026")).hasMessageContaining("senha atual");
        assertThatThrownBy(() -> atualizar("errada","Conta!Forte2026")).hasMessageContaining("senha atual");
        assertThatThrownBy(() -> atualizar("atual",null)).hasMessageContaining("nova senha");
        when(encoder.matches("atual","hash")).thenReturn(true); when(encoder.encode("Conta!Forte2026")).thenReturn("nova-hash");
        assertThat(atualizar("atual","Conta!Forte2026").getSenha()).isEqualTo("nova-hash");
    }
}
