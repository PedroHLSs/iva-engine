package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O CSV de classificação com CBS e IBS em colunas separadas, aceito desde
 * 14/09/2026.
 *
 * <p>A forma de coluna única continua coberta por
 * {@code ImportadorClassificacaoTributariaCsvTest}, sem alteração. Aqui fica a
 * garantia que a emenda precisa dar: juntar o par nunca escolhe um dos lados.</p>
 */
class ImportadorClassificacaoTributariaPorTributoCsvTest {

    private static final String CABECALHO_POR_TRIBUTO = "codigo;cstsCompativeis;"
            + "dispositivoLegal_cbs;dispositivoLegal_ibs;indicadorDeBeneficio;reducao_cbs;reducao_ibs;"
            + "camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;"
            + "fonteNormativa_cbs;fonteNormativa_ibs;natureza";

    private final ImportadorClassificacaoTributariaCsv importador = new ImportadorClassificacaoTributariaCsv();

    @Test
    void deveGravarUmValorSoQuandoCbsEIbsCoincidem() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;99,99;99,99;;"
                        + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO");

        assertThat(lida.dispositivoLegal()).isEqualTo("Dispositivo ficticio");
        assertThat(lida.fonteNormativa()).isEqualTo("FONTE FICTICIA v0.0");
        assertThat(lida.percentualReducao().orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveGuardarOsDoisDispositivosRotuladosQuandoDivergem() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio A;Dispositivo ficticio B;false;;;;"
                        + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO");

        assertThat(lida.dispositivoLegal())
                .isEqualTo("CBS: Dispositivo ficticio A | IBS: Dispositivo ficticio B");
    }

    @Test
    void deveGuardarAsDuasFontesRotuladasQuandoDivergem() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;;;;"
                        + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v9.9;FICTICIO");

        assertThat(lida.fonteNormativa())
                .isEqualTo("CBS: FONTE FICTICIA v0.0 | IBS: FONTE FICTICIA v9.9");
    }

    @Test
    void deveRecusarALinhaQuandoAReducaoDivergeEmVezDeEscolherUmDosValores() {
        String csv = CABECALHO_POR_TRIBUTO + "\n"
                + "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;99,99;11,11;;"
                + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO\n";

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("\"99,99\"")
                .hasMessageContaining("\"11,11\"")
                .hasMessageContaining("não escolhe");
    }

    @Test
    void deveRecusarQuandoSoUmDosTributosDeclaraReducao() {
        String csv = CABECALHO_POR_TRIBUTO + "\n"
                + "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;99,99;;;"
                + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO\n";

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("em branco");
    }

    @Test
    void deveManterReducaoNaoDeclaradaQuandoOsDoisTributosVemEmBranco() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;;;;"
                        + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO");

        assertThat(lida.percentualReducao()).isEmpty();
    }

    @Test
    void deveTratarVirgulaEPontoComoOMesmoValorNaComparacaoDaReducao() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;99,99;99.99;;"
                        + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO");

        assertThat(lida.percentualReducao().orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveRecusarReducaoEscritaComCasasDecimaisDiferentes() {
        // O mesmo número, escrito de dois jeitos: guardar um deles seria escolher
        // a escala que a tela vai exibir.
        String csv = CABECALHO_POR_TRIBUTO + "\n"
                + "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;99,9;99,90;;"
                + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO\n";

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("casas decimais");
    }

    @Test
    void deveRecusarCelulaEmBrancoNumDosTributosComoARecusaDaColunaUnica() {
        String csv = CABECALHO_POR_TRIBUTO + "\n"
                + "XXX001;AAA;Dispositivo ficticio;;false;;;;"
                + "1900-01-01;;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO\n";

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("Linha 2")
                .hasMessageContaining("dispositivoLegal_ibs");
    }

    @Test
    void deveRecusarCabecalhoComAsDuasFormasParaOMesmoCampo() {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;dispositivoLegal_cbs;dispositivoLegal_ibs;\
                indicadorDeBeneficio;percentualReducao;camposObrigatoriosCondicionados;\
                vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;Dispositivo ficticio;\
                false;;;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """;

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("uma forma só");
    }

    @Test
    void deveRecusarCabecalhoComOParPelaMetade() {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;reducao_cbs;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX001;AAA;Dispositivo ficticio;false;99,99;;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """;

        assertThatThrownBy(() -> importador.importar(ArquivoDeTeste.conteudo(csv)))
                .isInstanceOf(ImportacaoDeCatalogoInvalida.class)
                .hasMessageContaining("\"reducao_cbs\" sem \"reducao_ibs\"");
    }

    @Test
    void deveAceitarAsDuasFormasNoMesmoArquivoDesdeQueCadaCampoUseUmaSo() throws IOException {
        String csv = """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;reducao_cbs;reducao_ibs;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX001;AAA;Dispositivo ficticio;false;99,99;99,99;;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                """;

        ClassificacaoTributaria lida = importador.importar(ArquivoDeTeste.conteudo(csv)).registros().get(0);

        assertThat(lida.dispositivoLegal()).isEqualTo("Dispositivo ficticio");
        assertThat(lida.percentualReducao().orElseThrow()).isEqualByComparingTo("99.99");
    }

    @Test
    void deveAceitarAsDatasNoFormatoDiaMesAnoNaFormaPorTributo() throws IOException {
        ClassificacaoTributaria lida = importarUma(
                "XXX001;AAA;Dispositivo ficticio;Dispositivo ficticio;false;;;;"
                        + "01/01/1900;31/12/1900;FONTE FICTICIA v0.0;FONTE FICTICIA v0.0;FICTICIO");

        assertThat(lida.vigenciaInicio()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(lida.vigenciaFim()).contains(LocalDate.of(1900, 12, 31));
    }

    private ClassificacaoTributaria importarUma(String linhaDeDados) throws IOException {
        String csv = CABECALHO_POR_TRIBUTO + "\n" + linhaDeDados + "\n";
        return importador.importar(ArquivoDeTeste.conteudo(csv)).registros().get(0);
    }
}
