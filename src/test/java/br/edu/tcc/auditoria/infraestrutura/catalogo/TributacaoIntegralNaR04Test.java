package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;
import br.edu.tcc.auditoria.dominio.regras.ContextoNormativoFalso;
import br.edu.tcc.auditoria.dominio.regras.RegraTratamentoDeAnexoNaoAproveitado;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A coluna {@code tributacaoIntegral} do CSV de classificação, do importador real
 * até a R04.
 *
 * <p>Até a R04 1.0.0, "tributação integral" era deduzida: sem marca de benefício
 * e sem redução informada. Um código que declara redução zero escrita por extenso
 * ({@code 0;0}) ficava com redução presente e, portanto, "não integral" — e a R04
 * respondia conforme justamente no caso que ela existe para apontar. Desde a 1.1.0
 * o catálogo declara o fato numa coluna própria, e a redução deixou de ser
 * critério.</p>
 *
 * <p>Todos os valores são fictícios: código {@code XXX009}, CST {@code AAA}, NCM
 * zerado, datas em 1900. Nenhum deles é afirmação sobre a legislação.</p>
 */
class TributacaoIntegralNaR04Test {

    private static final String CABECALHO = "codigo;cstsCompativeis;"
            + "dispositivoLegal_cbs;dispositivoLegal_ibs;indicadorDeBeneficio;reducao_cbs;reducao_ibs;"
            + "camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;"
            + "fonteNormativa_cbs;fonteNormativa_ibs;natureza";

    private static final String CABECALHO_COM_COLUNA = CABECALHO + ";tributacaoIntegral";

    private static final String CODIGO = "XXX009";

    private static final RegraTratamentoDeAnexoNaoAproveitado R04 =
            new RegraTratamentoDeAnexoNaoAproveitado(CenarioFicticio.coberturaTotal().itensDeAnexo());

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    /* --- R04, pelo importador real -------------------------------------------- */

    @Test
    void deveApontarQuandoOCatalogoDeclaraIntegralMesmoComReducaoZeroEscrita() throws IOException {
        Avaliacao avaliacao = avaliarComNcmEmAnexo(linha("false", "0", "0", "S"));

        assertThat(avaliacao.resultado())
                .describedAs("redução zero escrita não pode esconder a tributação integral declarada")
                .isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.INFORMATIVA);
    }

    @Test
    void deveDizerConformeQuandoOCatalogoDeclaraNaoIntegralAindaQueComReducaoZero() throws IOException {
        Avaliacao avaliacao = avaliarComNcmEmAnexo(linha("false", "0", "0", "N"));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoACelulaVemEmBranco() throws IOException {
        Avaliacao avaliacao = avaliarComNcmEmAnexo(linha("false", "0", "0", ""));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(CODIGO)
                .contains("tributacaoIntegral");
    }

    @Test
    void naoDeveAvaliarQuandoOArquivoNaoTemAColuna() throws IOException {
        String semColuna = CODIGO + ";AAA;Dispositivo ficticio;Dispositivo ficticio;false;0;0;;"
                + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO";

        ClassificacaoTributaria lida = importarUma(CABECALHO, semColuna);

        assertThat(avaliarComNcmEmAnexo(lida).resultado())
                .describedAs("arquivo anterior à coluna continua importando, e a R04 diz que não sabe")
                .isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    /* --- importador ------------------------------------------------------------ */

    @Test
    void deveRecusarValorDiferenteDeSOuN() {
        CargaRecusada recusa = recusaDe(linha("false", "0", "0", "SIM"));

        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.linha()).contains(2);
            assertThat(recusada.coluna()).contains("tributacaoIntegral");
            assertThat(recusada.valor()).contains("SIM");
        });
    }

    @Test
    void deveRecusarIntegralComMarcaDeBeneficio() {
        CargaRecusada recusa = recusaDe(linha("true", "0", "0", "S"));

        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.linha()).contains(2);
            assertThat(recusada.coluna()).contains("tributacaoIntegral");
        });
        assertThat(recusa.getMessage()).contains("indicadorDeBeneficio");
    }

    @Test
    void deveRecusarIntegralComReducaoDiferenteDeZero() {
        CargaRecusada recusa = recusaDe(linha("false", "99,99", "99,99", "S"));

        assertThat(recusa.recusadas()).singleElement().satisfies(recusada -> {
            assertThat(recusada.linha()).contains(2);
            assertThat(recusada.coluna()).contains("tributacaoIntegral");
        });
        assertThat(recusa.getMessage()).contains("99,99");
    }

    @Test
    void deveRecusarACargaInteiraMesmoComUmaLinhaBoa() {
        String csv = CABECALHO_COM_COLUNA + "\n"
                + linha("false", "0", "0", "N") + "\n"
                + linha("false", "0", "0", "X").replace(CODIGO, "XXX008") + "\n";

        CargaRecusada recusa = catchThrowableOfType(
                () -> importador.importar(ArquivoDeTeste.conteudo(csv)), CargaRecusada.class);

        assertThat(recusa).describedAs("tudo ou nada, como na Etapa 12").isNotNull();
        assertThat(recusa.recusadas()).singleElement()
                .satisfies(recusada -> assertThat(recusada.linha()).contains(3));
    }

    /* --- auxiliares ------------------------------------------------------------ */

    private static String linha(String beneficio, String reducaoCbs, String reducaoIbs, String integral) {
        return CODIGO + ";AAA;Dispositivo ficticio;Dispositivo ficticio;" + beneficio + ";"
                + reducaoCbs + ";" + reducaoIbs + ";;1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;"
                + "FICTICIO;" + integral;
    }

    private Avaliacao avaliarComNcmEmAnexo(String linhaComColuna) throws IOException {
        return avaliarComNcmEmAnexo(importarUma(CABECALHO_COM_COLUNA, linhaComColuna));
    }

    private static Avaliacao avaliarComNcmEmAnexo(ClassificacaoTributaria classificacao) {
        ItemDocumento item = ConstrutorDeItem.item().ncm(CenarioFicticio.NCM).classificacao(CODIGO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.itemAnexo(CenarioFicticio.NCM, CenarioFicticio.ANEXO))
                .com(classificacao);
        return R04.avaliar(item, CenarioFicticio.documento(), catalogo);
    }

    private ClassificacaoTributaria importarUma(String cabecalho, String linha) throws IOException {
        List<ClassificacaoTributaria> lidas =
                importador.importar(ArquivoDeTeste.conteudo(cabecalho + "\n" + linha + "\n")).registros();
        assertThat(lidas).hasSize(1);
        return lidas.get(0);
    }

    private CargaRecusada recusaDe(String linha) {
        CargaRecusada recusa = catchThrowableOfType(
                () -> importador.importar(ArquivoDeTeste.conteudo(CABECALHO_COM_COLUNA + "\n" + linha + "\n")),
                CargaRecusada.class);
        assertThat(recusa).describedAs("a linha deveria ter sido recusada").isNotNull();
        return recusa;
    }
}
