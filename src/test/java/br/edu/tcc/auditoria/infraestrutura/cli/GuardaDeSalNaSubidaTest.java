package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;
import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.OrigemDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;
import br.edu.tcc.auditoria.infraestrutura.sal.SalTrocado;
import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/** O guarda que recusa a subida com sal trocado. */
class GuardaDeSalNaSubidaTest {

    private static final String SAL_A = "sal-ficticio-de-teste-aaaaaaaaaaaaaaaaaa";
    private static final String SAL_B = "sal-ficticio-de-teste-bbbbbbbbbbbbbbbbbb";

    private final SaidaDeTeste saida = new SaidaDeTeste();

    @Test
    void deveRegistrarAImpressaoDigitalQuandoNaoHouverNenhumaEOAcervoEstiverVazio() {
        RegistroDeTeste registro = new RegistroDeTeste();
        AcervoDeTeste acervo = new AcervoDeTeste(0);

        guarda(SAL_A, registro, acervo).run("auditar");

        assertThat(registro.registrada()).isPresent();
        assertThat(registro.registrada().orElseThrow().sobreAcervoExistente()).isFalse();
    }

    @Test
    void deveAdotarAImpressaoDigitalSobreAcervoJaExistenteEDizerQueNaoConferiuNada() {
        RegistroDeTeste registro = new RegistroDeTeste();
        AcervoDeTeste acervo = new AcervoDeTeste(12);

        guarda(SAL_A, registro, acervo).run("auditar");

        assertThat(registro.registrada().orElseThrow().sobreAcervoExistente())
                .as("a ressalva fica gravada: não havia com o que comparar")
                .isTrue();
        assertThat(saida.texto()).contains("Não houve o que verificar desta vez");
    }

    @Test
    void naoDeveRecusarQuandoAImpressaoDigitalConferir() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatCode(() -> guarda(SAL_A, registro, new AcervoDeTeste(12)).run("auditar"))
                .doesNotThrowAnyException();
    }

    @Test
    void deveRecusarQuandoAImpressaoDigitalDivergirSobreAcervoPovoado() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatThrownBy(() -> guarda(SAL_B, registro, new AcervoDeTeste(340)).run("auditar"))
                .isInstanceOf(SalTrocado.class)
                .hasMessageContaining("340");

        assertThat(saida.texto())
                .contains("RECUSADO")
                .contains(impressaoDe(SAL_A).valor())
                .contains(impressaoDe(SAL_B).valor());
    }

    @Test
    void aRecusaNaoDeveMostrarOSal() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatThrownBy(() -> guarda(SAL_B, registro, new AcervoDeTeste(340)).run("auditar"))
                .isInstanceOf(SalTrocado.class);

        assertThat(saida.texto()).doesNotContain(SAL_A).doesNotContain(SAL_B);
    }

    @Test
    void aRecusaDeveDizerQueAsTratativasNaoEstaoEmRisco() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatThrownBy(() -> guarda(SAL_B, registro, new AcervoDeTeste(1)).run("auditar"))
                .isInstanceOf(SalTrocado.class);

        assertThat(saida.texto()).contains("tratativas NÃO estão em risco");
    }

    @Test
    void naoDeveRecusarQuandoADivergenciaAcontecerSobreAcervoVazio() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatCode(() -> guarda(SAL_B, registro, new AcervoDeTeste(0)).run("auditar"))
                .doesNotThrowAnyException();
        assertThat(registro.registrada().orElseThrow().impressao()).isEqualTo(impressaoDe(SAL_B));
    }

    @Test
    void deveDeixarPassarODiagnosticoEORecomecoAindaQueRecusasseOsDemais() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        for (String permitido : List.of(ComandoDiagnosticarSal.NOME, ComandoRecomecarDoZero.NOME)) {
            assertThatCode(() -> guarda(SAL_B, registro, new AcervoDeTeste(340)).run(permitido))
                    .as("%s precisa rodar justamente quando o guarda estaria recusando", permitido)
                    .doesNotThrowAnyException();
        }
    }

    /**
     * A regra é de permissão, não de exclusão.
     *
     * <p>Um comando que ninguém pensou em proteger — inclusive um que ainda não
     * existe — é barrado por não estar na lista. Se a lista fosse de barrados,
     * este teste passaria com o comando atravessando o guarda, que é o defeito.</p>
     */
    @Test
    void deveRecusarUmComandoQueNinguemPensouEmProteger() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatThrownBy(() ->
                guarda(SAL_B, registro, new AcervoDeTeste(340)).run("comando-inventado-amanha"))
                .isInstanceOf(SalTrocado.class);
    }

    @Test
    void semComandoNenhumNaoDeveConferirNada() {
        RegistroDeTeste registro = new RegistroDeTeste();
        registro.registrar(impressaoDe(SAL_A), OrigemDoSal.ARQUIVO_LOCAL, false);

        assertThatCode(() -> guarda(SAL_B, registro, new AcervoDeTeste(340)).run())
                .doesNotThrowAnyException();
    }

    private GuardaDeSalNaSubida guarda(
            String sal, RegistroDaImpressaoDigital registro, AcervoSalgado acervo) {

        SalResolvido resolvido = new SalResolvido(
                new SalDeInstalacao(sal), OrigemDoSal.ARQUIVO_LOCAL, Optional.empty(), Optional.empty());
        return new GuardaDeSalNaSubida(resolvido, registro, acervo, saida);
    }

    private static ImpressaoDigitalDoSal impressaoDe(String sal) {
        return ImpressaoDigitalDoSal.de(new SalDeInstalacao(sal));
    }

    private static final class RegistroDeTeste implements RegistroDaImpressaoDigital {

        private Registro registro;

        @Override
        public Optional<Registro> registrada() {
            return Optional.ofNullable(registro);
        }

        @Override
        public void registrar(ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobre) {
            this.registro = new Registro(impressao, origem, sobre);
        }
    }

    private record AcervoDeTeste(long documentos) implements AcervoSalgado {

        @Override
        public long quantidadeDeDocumentos() {
            return documentos;
        }

        @Override
        public Apagamento apagar() {
            throw new UnsupportedOperationException("O guarda nunca apaga nada.");
        }
    }

    private static final class SaidaDeTeste implements Saida {

        private final List<String> linhas = new ArrayList<>();

        @Override
        public void linha(String texto) {
            linhas.add(texto);
        }

        String texto() {
            return String.join("\n", linhas);
        }
    }
}
