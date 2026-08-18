package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra de ouro, verificada de uma vez sobre o conjunto inteiro.
 *
 * <p>Cada regra tem seu próprio teste de {@code NAO_AVALIADO}. Este aqui existe
 * por outra razão: uma regra nova acrescentada ao conjunto passa a ser cobrada
 * automaticamente, sem depender de alguém lembrar de escrever o teste. Se ela
 * devolver {@code CONFORME} diante de um catálogo que nada respondeu, este teste
 * falha citando o identificador dela.</p>
 *
 * <p>O cenário é o pior possível e o mais realista de todos: item completo,
 * catálogo sem nenhum registro, e nenhuma tabela cobrindo a data do documento —
 * uma carga que não aconteceu, ou que aconteceu para outro período. A resposta
 * correta de todas as regras é a mesma: não sei dizer, e este é o motivo.</p>
 */
class AusenciaDeDadoNaoEConformidadeTest {

    private static final ConjuntoRegras CONJUNTO = ConjuntoRegras.padrao(
            CenarioFicticio.coberturaForaDaData(), ToleranciaDeValor.exata());

    @Test
    void nenhumaRegraDeveDizerConformeComOCatalogoMudoESemCobertura() {
        List<Avaliacao> avaliacoes = avaliarTudo();

        assertThat(avaliacoes)
                .as("Regra que diz conforme sem ter consultado tabela nenhuma afirma, num relatório de "
                        + "auditoria, que conferiu o que não conferiu.")
                .noneSatisfy(avaliacao ->
                        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.CONFORME));
    }

    @Test
    void nenhumaRegraDeveApontarComOCatalogoMudoESemCobertura() {
        List<Avaliacao> avaliacoes = avaliarTudo();

        assertThat(avaliacoes)
                .as("Sem catálogo carregado para a data, apontar é acusar o documento por falta de carga.")
                .noneSatisfy(avaliacao ->
                        assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.ACHADO));
    }

    @Test
    void todaRegraDeveRegistrarOMotivoDeNaoTerAvaliado() {
        assertThat(avaliarTudo()).allSatisfy(avaliacao -> {
            assertThat(avaliacao.resultado()).isEqualTo(ResultadoAvaliacao.NAO_AVALIADO);
            assertThat(avaliacao.motivoDaNaoAvaliacao()).isPresent();
            assertThat(avaliacao.motivoDaNaoAvaliacao().orElseThrow()).isNotBlank();
        });
    }

    @Test
    void todaAvaliacaoDeveDizerDeQualRegraEDeQualItemEla() {
        assertThat(avaliarTudo()).allSatisfy(avaliacao -> {
            assertThat(avaliacao.regraId()).isNotBlank();
            assertThat(avaliacao.regraVersao()).isNotBlank();
            assertThat(avaliacao.numeroItem()).hasValue(1);
        });
    }

    @Test
    void oConjuntoDeveCobrirAsSeteRegras() {
        assertThat(CONJUNTO.regras()).hasSize(7);
    }

    private static List<Avaliacao> avaliarTudo() {
        ItemDocumento item = itemCompleto();
        return CONJUNTO.regras().stream()
                .map(regra -> regra.avaliar(item, CenarioFicticio.documento(), ContextoNormativoFalso.vazio()))
                .toList();
    }

    /** Item com todos os campos preenchidos: o que falta é o catálogo, não o documento. */
    private static ItemDocumento itemCompleto() {
        String valor = new BigDecimal("99.99").toPlainString();
        return ConstrutorDeItem.item()
                .ncm(CenarioFicticio.NCM)
                .cfop("9999")
                .cstIbs(CenarioFicticio.CST)
                .cstCbs(CenarioFicticio.CST)
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs(valor)
                .baseCalculoCbs(valor)
                .aliquotaIbsUf(valor)
                .aliquotaIbsMunicipal(valor)
                .aliquotaCbs(valor)
                .valorIbsUf(valor)
                .valorIbsMunicipal(valor)
                .valorCbs(valor)
                .construir();
    }
}
