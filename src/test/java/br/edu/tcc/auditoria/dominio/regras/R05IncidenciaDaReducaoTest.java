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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// D017 (03/10/2026): a R05 lê no catálogo, e não numa lista de CST escrita em código, se a redução do cClassTrib incide sobre a base. BASE com redução diferente de zero é NAO_AVALIADO (D5); ALIQUOTA, ou não declarado (D1), aplica a redução sobre a alíquota. Valores fictícios: base 1000,00, alíquota 99,99, que dão 999,90 cheio e 399,96 com redução de 60; código XXX001, CST AAA.
class R05IncidenciaDaReducaoTest {

    private static final RegraValorDeTributoConfere REGRA = new RegraValorDeTributoConfere(
            ToleranciaDeValor.exata(), CenarioFicticio.coberturaTotal().classificacoesTributarias());

    private static final String VALOR_CHEIO = "999.90";
    private static final String VALOR_REDUZIDO = "399.96";

    @Test
    void reducaoDeclaradaSobreABaseNaoDeveSerAvaliada() {
        Avaliacao avaliacao = avaliar(VALOR_REDUZIDO, "60", Optional.of(IncidenciaDaReducao.BASE));

        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
        assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow())
                .contains("redução de base não suportada")
                .contains("reducaoIncideSobre");
    }

    @Test
    void reducaoDeclaradaSobreAAliquotaDeveEntrarNaConta() {
        assertThat(avaliar(VALOR_REDUZIDO, "60", Optional.of(IncidenciaDaReducao.ALIQUOTA)).resultado())
                .isEqualTo(ResultadoAvaliacao.CONFORME);
        assertThat(avaliar(VALOR_CHEIO, "60", Optional.of(IncidenciaDaReducao.ALIQUOTA)).resultado())
                .isEqualTo(ResultadoAvaliacao.ACHADO);
    }

    // D1: a coluna de redução do catálogo é de alíquota; quem declara a exceção é a carga.
    @Test
    void incidenciaNaoDeclaradaDeveSerLidaComoAliquota() {
        assertThat(avaliar(VALOR_REDUZIDO, "60", Optional.empty()).resultado())
                .isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Sem redução, a incidência não é consultada: a conta é a da alíquota cheia.
    @Test
    void reducaoZeroSobreABaseDeveSerFatorUm() {
        assertThat(avaliar(VALOR_CHEIO, "0", Optional.of(IncidenciaDaReducao.BASE)).resultado())
                .isEqualTo(ResultadoAvaliacao.CONFORME);
    }

    // Método auxiliar que monta o item e o catálogo fictícios e roda a R05.
    private static Avaliacao avaliar(String valor, String reducao, Optional<IncidenciaDaReducao> incidencia) {
        ItemDocumento item = ConstrutorDeItem.item()
                .classificacao(CenarioFicticio.CODIGO)
                .cstIbs(CenarioFicticio.CST).cstCbs(CenarioFicticio.CST)
                .baseCalculoIbs("1000.00").baseCalculoCbs("1000.00")
                .valorIbsUf(valor).valorIbsMunicipal(valor).valorCbs(valor)
                .construir();
        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CenarioFicticio.CODIGO),
                Set.of(new CodigoCst(CenarioFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                true,
                Optional.of(new BigDecimal(reducao)),
                incidencia,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                CenarioFicticio.procedencia());
        ContextoNormativoFalso catalogo = ContextoNormativoFalso.vazio()
                .com(classificacao)
                .com(CenarioFicticio.aliquota(Tributo.IBS_UF, "99.99"))
                .com(CenarioFicticio.aliquota(Tributo.IBS_MUN, "99.99"))
                .com(CenarioFicticio.aliquota(Tributo.CBS, "99.99"));
        return REGRA.avaliar(item, CenarioFicticio.documento(), catalogo);
    }
}
