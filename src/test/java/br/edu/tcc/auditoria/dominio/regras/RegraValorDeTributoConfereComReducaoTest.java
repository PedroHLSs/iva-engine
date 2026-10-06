package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.catalogo.CatalogoFicticio;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R05 1.1.0 — a redução de alíquota que o catálogo declara para o cClassTrib
 * entra na conta: esperado = base × alíquota × (1 − redução/100).
 *
 * <p>Decisões do usuário de 30/09/2026 (D1, D4 e D5): a redução do catálogo é
 * redução de alíquota; redução em branco não é avaliada; CST de redução de base
 * com redução diferente de zero não é avaliado; código fora do catálogo continua
 * como na 1.0.0.</p>
 *
 * <p>Os números são fictícios: código {@code XXX001}, base 1000,00 e alíquota
 * 99,99, que dão 999,90 cheio e 399,96 com a redução de 60 que o usuário pediu
 * para o teste. Nenhum deles é afirmação sobre a legislação.</p>
 */
class RegraValorDeTributoConfereComReducaoTest {

    private static final RegraValorDeTributoConfere REGRA_EXATA =
            new RegraValorDeTributoConfere(
                    ToleranciaDeValor.exata(), CenarioFicticio.coberturaTotal().classificacoesTributarias());

    private static final String BASE = "1000.00";
    private static final String PERCENTUAL = "99.99";
    private static final String VALOR_CHEIO = "999.90";
    private static final String VALOR_REDUZIDO = "399.96";


    @Test
    void deveDizerConformeQuandoOValorDeclaradoJaTrazAReducaoDoCatalogo() {
        Avaliacao avaliacao = avaliar(itemCom(VALOR_REDUZIDO), comReducao("60"));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveApontarQuandoOValorDeclaradoIgnoraAReducaoDoCatalogo() {
        Avaliacao avaliacao = avaliar(itemCom(VALOR_CHEIO), comReducao("60"));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().quantiaEmRisco().orElseThrow())
                .describedAs("três tributos, cada um com 999,90 − 399,96 de diferença")
                .isEqualByComparingTo(new BigDecimal("1799.82"));
    }

    @Test
    void deveDizerConformeQuandoAReducaoTotalLevaOValorAZero() {
        Avaliacao avaliacao = avaliar(itemCom("0.00"), comReducao("100"));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveTratarReducaoZeroComoFatorUm() {
        Avaliacao avaliacao = avaliar(itemCom(VALOR_CHEIO), comReducao("0"));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNaoDeclaraReducao() {
        ContextoNormativoFalso catalogo = aliquotas().com(CenarioFicticio.classificacao(
                CenarioFicticio.CODIGO, false, Optional.empty(), List.of(), CenarioFicticio.CST));

        Avaliacao avaliacao = avaliar(itemCom(VALOR_CHEIO), catalogo);

        assertThat(avaliacao.resultado())
                .describedAs("redução em branco não é zero, e a regra não escolhe o fator")
                .isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(CenarioFicticio.CODIGO);
    }

    // Até 03/10/2026 este teste usava o CST 222, um código real, no item e no catálogo, porque a R05 1.1.0 deduzia a redução de base de uma lista de CST escrita em código. Desde a R05 1.3.0 (D017) o catálogo declara a incidência, e o teste declara BASE.
    @Test
    void naoDeveAvaliarReducaoDeBaseComReducaoDiferenteDeZero() {
        ItemDocumento item = itemCom(VALOR_REDUZIDO);
        ContextoNormativoFalso catalogo = aliquotas().com(new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                true,
                Optional.of(new BigDecimal("60")),
                Optional.of(IncidenciaDaReducao.BASE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CenarioFicticio.procedencia()));

        Avaliacao avaliacao = avaliar(item, catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains("redução de base não suportada");
    }

    @Test
    void deveManterOComportamentoDaVersaoAnteriorQuandoOCodigoNaoEstaNoCatalogo() {
        // Sem registro do código, não há redução a aplicar: fator 1, como na 1.0.0.
        assertThat(avaliar(itemCom(VALOR_CHEIO), aliquotas()).resultado())
                .isEqualTo(ResultadoAvaliacao.CONFORME);
        assertThat(avaliar(itemCom(VALOR_REDUZIDO), aliquotas()).resultado())
                .isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void deveManterOComportamentoDaVersaoAnteriorQuandoOItemNaoDeclaraCodigo() {
        ItemDocumento semCodigo = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .baseCalculoCbs(BASE)
                .valorIbsUf(VALOR_CHEIO)
                .valorIbsMunicipal(VALOR_CHEIO)
                .valorCbs(VALOR_CHEIO)
                .construir();

        assertThat(avaliar(semCodigo, comReducao("60")).resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Monta um item com o código fictício e o mesmo valor declarado nos três tributos.
    private static ItemDocumento itemCom(String valor) {
        return ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST)
                .cstCbs(CenarioFicticio.CST)
                .baseCalculoIbs(BASE)
                .baseCalculoCbs(BASE)
                .valorIbsUf(valor)
                .valorIbsMunicipal(valor)
                .valorCbs(valor)
                .construir();
    }

    // Catálogo com as três alíquotas e a classificação do código com a redução informada.
    private static ContextoNormativoFalso comReducao(String reducao) {
        return aliquotas().com(CenarioFicticio.classificacao(
                CenarioFicticio.CODIGO, true, Optional.of(new BigDecimal(reducao)), List.of(),
                CenarioFicticio.CST));
    }

    // Catálogo só com as três alíquotas, sem nenhuma classificação.
    private static ContextoNormativoFalso aliquotas() {
        return ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.CBS, PERCENTUAL));
    }

    private static Avaliacao avaliar(ItemDocumento item, ContextoNormativoFalso catalogo) {
        return REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogo);
    }
}
