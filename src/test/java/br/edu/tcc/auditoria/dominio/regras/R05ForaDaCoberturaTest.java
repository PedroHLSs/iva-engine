package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// D016 (03/10/2026): a R05 lê a redução na tabela de classificações e, quando o código não está lá, só assume "sem redução" (fator 1, decisão D4) se a tabela cobre a data do documento. Fora da cobertura, silêncio é falta de dado (D004): NAO_AVALIADO. Valores fictícios: base 1000,00, alíquota 99,99, que dão 999,90 cheio e 399,96 com redução de 60; código XXX001; datas em 1900.
class R05ForaDaCoberturaTest {

    private static final String BASE = "1000.00";
    private static final String PERCENTUAL = "99.99";
    private static final String VALOR_CHEIO = "999.90";
    private static final String VALOR_REDUZIDO = "399.96";

    private static final ProcedenciaNormativa COBRE_A_DATA = CenarioFicticio.coberturaTotal().classificacoesTributarias();
    private static final ProcedenciaNormativa NAO_COBRE_A_DATA =
            CenarioFicticio.coberturaForaDaData().classificacoesTributarias();

    // O cenário demonstrado na revisão: a tabela de classificações não cobre a data, as alíquotas estão lá, e nenhum código das notas está na tabela. Nove notas, com valores reduzidos (a de controle), cheios, e misturados.
    @Test
    void comAClassificacaoForaDaCoberturaNenhumaDasNoveNotasDeveConcluir() {
        List<ItemDocumento> notas = List.of(
                item(VALOR_REDUZIDO, VALOR_REDUZIDO, VALOR_REDUZIDO),
                item(VALOR_CHEIO, VALOR_CHEIO, VALOR_CHEIO),
                item(VALOR_REDUZIDO, VALOR_REDUZIDO, VALOR_CHEIO),
                item(VALOR_CHEIO, VALOR_CHEIO, VALOR_REDUZIDO),
                item(VALOR_REDUZIDO, "0.00", VALOR_REDUZIDO),
                item(VALOR_CHEIO, "0.00", VALOR_CHEIO),
                item("0.00", "0.00", "0.00"),
                item("123.45", "123.45", "123.45"),
                item(VALOR_REDUZIDO, VALOR_CHEIO, VALOR_REDUZIDO));

        for (ItemDocumento nota : notas) {
            Avaliacao avaliacao = avaliar(nota, NAO_COBRE_A_DATA);
            assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
            assertThat(avaliacao.achado()).isEmpty();
        }
    }

    @Test
    void aNotaDeControleNaoDeveReceberApontamentoForaDaCobertura() {
        Avaliacao avaliacao = avaliar(item(VALOR_REDUZIDO, VALOR_REDUZIDO, VALOR_REDUZIDO), NAO_COBRE_A_DATA);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(((Avaliacao.NaoAvaliada) avaliacao).motivo())
                .contains(CenarioFicticio.CODIGO)
                .contains(CenarioFicticio.DATA_EMISSAO.toString())
                .contains("cobertura");
    }

    @Test
    void notaComValorCheioNaoDeveSerConformeForaDaCobertura() {
        Avaliacao avaliacao = avaliar(item(VALOR_CHEIO, VALOR_CHEIO, VALOR_CHEIO), NAO_COBRE_A_DATA);

        assertThat(avaliacao.resultado())
                .describedAs("fora da cobertura não há como saber se o código tem redução")
                .isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
    }

    // Dentro da cobertura, código que a tabela não traz continua sem redução (D4): o catálogo foi carregado para a data e não o conhece.
    @Test
    void dentroDaCoberturaCodigoAusenteDeveSeguirAD4ComValorCheio() {
        assertThat(avaliar(item(VALOR_CHEIO, VALOR_CHEIO, VALOR_CHEIO), COBRE_A_DATA).resultado())
                .isEqualTo(ResultadoAvaliacao.CONFORME);
        assertThat(avaliar(item(VALOR_REDUZIDO, VALOR_REDUZIDO, VALOR_REDUZIDO), COBRE_A_DATA).resultado())
                .isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    // Registro encontrado é declaração do catálogo, vigente na data: a cobertura da tabela só decide o que fazer com o silêncio.
    @Test
    void codigoEncontradoDeveAplicarAReducaoMesmoComACoberturaForaDaData() {
        ContextoNormativoFalso catalogo = aliquotas().com(CenarioFicticio.classificacao(
                CenarioFicticio.CODIGO, true, Optional.of(new BigDecimal("60")), List.of(), CenarioFicticio.CST));

        Avaliacao avaliacao = regra(NAO_COBRE_A_DATA).avaliar(
                item(VALOR_REDUZIDO, VALOR_REDUZIDO, VALOR_REDUZIDO), CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Item sem cClassTrib não consulta a tabela, e a D4 manda fator 1 — a cobertura da tabela não entra.
    @Test
    void itemSemCodigoDeveSeguirAD4MesmoForaDaCobertura() {
        ItemDocumento semCodigo = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE).baseCalculoCbs(BASE)
                .valorIbsUf(VALOR_CHEIO).valorIbsMunicipal(VALOR_CHEIO).valorCbs(VALOR_CHEIO)
                .construir();

        assertThat(avaliar(semCodigo, NAO_COBRE_A_DATA).resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Método auxiliar que monta o item fictício com o código fora da tabela e os três valores indicados.
    private static ItemDocumento item(String valorIbsUf, String valorIbsMunicipal, String valorCbs) {
        return ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .cstCbs(CenarioFicticio.CST)
                .baseCalculoIbs(BASE)
                .baseCalculoCbs(BASE)
                .valorIbsUf(valorIbsUf)
                .valorIbsMunicipal(valorIbsMunicipal)
                .valorCbs(valorCbs)
                .construir();
    }

    // Método auxiliar que roda a R05 com tolerância exata, as três alíquotas e nenhuma classificação.
    private static Avaliacao avaliar(ItemDocumento item, ProcedenciaNormativa cobertura) {
        return regra(cobertura).avaliar(item, CenarioFicticio.documento(), aliquotas());
    }

    private static RegraValorDeTributoConfere regra(ProcedenciaNormativa cobertura) {
        return new RegraValorDeTributoConfere(ToleranciaDeValor.exata(), cobertura);
    }

    // Catálogo só com as três alíquotas fictícias, sem nenhuma classificação.
    private static ContextoNormativoFalso aliquotas() {
        return ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.CBS, PERCENTUAL));
    }
}
