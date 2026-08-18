package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R05 — o valor declarado confere com base × alíquota vigente, dentro da
 * tolerância?
 *
 * <p>Os números usados aqui são fictícios: base 100,00 e percentual 99,99, que
 * fazem o valor esperado ser 99,99 e a conta ser conferível a olho.</p>
 */
class RegraValorDeTributoConfereTest {

    private static final RegraValorDeTributoConfere REGRA_EXATA =
            new RegraValorDeTributoConfere(ToleranciaDeValor.exata());

    private static final String BASE = "100.00";
    private static final String PERCENTUAL = "99.99";
    private static final String VALOR_ESPERADO = "99.99";

    @Test
    void deveApontarValorQueNaoFechaComBaseVezesAliquotaVigente() {
        ItemDocumento item = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .valorIbsUf("0.00")
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL));

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().severidade()).isEqualTo(Severidade.GRAVE);
        assertThat(avaliacao.achado().orElseThrow().quantiaEmRisco().orElseThrow())
                .isEqualByComparingTo(new BigDecimal(VALOR_ESPERADO));
    }

    @Test
    void deveDizerConformeQuandoOsTresTributosFecham() {
        ItemDocumento item = itemCompleto(VALOR_ESPERADO, VALOR_ESPERADO, VALOR_ESPERADO);

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogoCompleto());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void naoDeveAvaliarQuandoOCatalogoNaoTrazAliquotaVigente() {
        // A regra de ouro: sem alíquota de referência não há conta a fazer, e o
        // relatório precisa dizer isso em vez de dar o item por conferido.
        ItemDocumento item = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .valorIbsUf(VALOR_ESPERADO)
                .construir();

        Avaliacao avaliacao =
                REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(Tributo.IBS_UF.name());
    }

    @Test
    void naoDeveEscolherEntreAliquotasDeAbrangenciasDiferentes() {
        // Qual abrangência se aplica é conteúdo normativo. Escolher uma seria
        // inventá-lo, e escolher "a primeira" seria inventá-lo em silêncio.
        ItemDocumento item = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .valorIbsUf(VALOR_ESPERADO)
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL, CenarioFicticio.ABRANGENCIA))
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, "0.01", CenarioFicticio.ABRANGENCIA_ALTERNATIVA));

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains(CenarioFicticio.ABRANGENCIA, CenarioFicticio.ABRANGENCIA_ALTERNATIVA);
    }

    @Test
    void naoDeveAvaliarQuandoOItemDeclarouValorSemDeclararBase() {
        ItemDocumento item = ConstrutorDeItem.item().valorIbsUf(VALOR_ESPERADO).construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL));

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains("baseCalculoIbs");
    }

    @Test
    void deveAcomodarDiferencaDentroDaTolerancia() {
        RegraValorDeTributoConfere regraTolerante =
                new RegraValorDeTributoConfere(ToleranciaDeValor.de(new BigDecimal("0.01")));
        ItemDocumento item = itemCompleto("99.98", VALOR_ESPERADO, VALOR_ESPERADO);

        Avaliacao avaliacao = regraTolerante.avaliar(item, CenarioFicticio.documento(), catalogoCompleto());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    @Test
    void deveApontarAMesmaDiferencaQuandoNaoCabeNaTolerancia() {
        RegraValorDeTributoConfere regraTolerante =
                new RegraValorDeTributoConfere(ToleranciaDeValor.de(new BigDecimal("0.01")));
        ItemDocumento item = itemCompleto("99.97", VALOR_ESPERADO, VALOR_ESPERADO);

        Avaliacao avaliacao = regraTolerante.avaliar(item, CenarioFicticio.documento(), catalogoCompleto());

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
        assertThat(avaliacao.achado().orElseThrow().quantiaEmRisco().orElseThrow())
                .isEqualByComparingTo(new BigDecimal("0.02"));
    }

    @Test
    void deveApontarDivergenciaAindaQueOutroTributoNaoTenhaSidoAvaliavel() {
        // A divergência encontrada é fato, e não pode ser engolida pela pendência
        // de outro par.
        ItemDocumento item = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .valorIbsUf("0.00")
                .construir();
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL));

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogo);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    @Test
    void naoDeveDizerConformeQuandoUmDosTresTributosFicouPendente() {
        ItemDocumento item = ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .valorIbsUf(VALOR_ESPERADO)
                .valorIbsMunicipal(VALOR_ESPERADO)
                .baseCalculoCbs(BASE)
                .valorCbs(VALOR_ESPERADO)
                .construir();
        ContextoNormativoFalso catalogoSemCbs = ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, PERCENTUAL));

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogoSemCbs);

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).contains(Tributo.CBS.name());
    }

    @Test
    void oValorEmRiscoDeveSomarAsDiferencasDeCadaTributoDivergente() {
        ItemDocumento item = itemCompleto("0.00", VALOR_ESPERADO, "0.00");

        Avaliacao avaliacao = REGRA_EXATA.avaliar(item, CenarioFicticio.documento(), catalogoCompleto());

        assertThat(avaliacao.achado().orElseThrow().quantiaEmRisco().orElseThrow())
                .isEqualByComparingTo(new BigDecimal("199.98"));
    }

    private static ItemDocumento itemCompleto(String valorIbsUf, String valorIbsMunicipal, String valorCbs) {
        return ConstrutorDeItem.item()
                .baseCalculoIbs(BASE)
                .baseCalculoCbs(BASE)
                .valorIbsUf(valorIbsUf)
                .valorIbsMunicipal(valorIbsMunicipal)
                .valorCbs(valorCbs)
                .construir();
    }

    private static ContextoNormativoFalso catalogoCompleto() {
        return ContextoNormativoFalso.vazio()
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, PERCENTUAL))
                .com(CenarioFicticio.aliquota(Tributo.CBS, PERCENTUAL));
    }
}
