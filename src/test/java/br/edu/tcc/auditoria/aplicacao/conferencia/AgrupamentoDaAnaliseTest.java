package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A tela do lote agrupa por parametrização, não por nota.
 *
 * <p>Cenário fictício: dois NCM de dígitos repetidos, dois códigos com sufixo
 * "FICT", e um item que não declarou nenhum dos dois. Nada aqui pode ser lido
 * como afirmação sobre a legislação.</p>
 */
@DisplayName("Agrupamento do lote: por cadastro, com o nível e a ordem declarados")
class AgrupamentoDaAnaliseTest {

    private static final String NCM_A = "00000000";
    private static final String NCM_B = "99999999";
    private static final String CT_A = "FICT-001";
    private static final String CT_B = "FICT-002";

    @Test
    void deveJuntarProdutosDeMesmoEnquadramentoESituacao() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        GrupoDeProdutos maiorRepeticao = grupoDe(agrupamento, NCM_A, CT_A,
                EstadoDeConferencia.POSSIVEL_DIVERGENCIA);

        assertThat(maiorRepeticao.quantidadeDeProdutos())
                .describedAs("um NCM mal classificado em duas notas continua sendo um erro de cadastro")
                .isEqualTo(2);
        assertThat(maiorRepeticao.quantidadeDeNotas()).isEqualTo(2);
        assertThat(maiorRepeticao.valorDosProdutos()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void deveSepararOMesmoEnquadramentoQuandoASituacaoDifere() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        assertThat(agrupamento.grupos())
                .describedAs("o mesmo NCM e cClassTrib com desfechos diferentes são grupos diferentes")
                .filteredOn(grupo -> grupo.chave().ncm().orElse("").equals(NCM_A))
                .hasSize(2);
    }

    @Test
    void deveOrdenarPorValorDosProdutosPorPadrao() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        assertThat(agrupamento.grupos())
                .extracting(GrupoDeProdutos::valorDosProdutos)
                .containsExactly(
                        new BigDecimal("2000.00"),
                        new BigDecimal("1500.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("1.00"));
    }

    @Test
    void deveOferecerAOrdenacaoAlternativaPorContagem() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.QUANTIDADE_DE_PRODUTOS);

        assertThat(agrupamento.grupos().get(0).quantidadeDeProdutos())
                .describedAs("a pergunta \"qual erro se repete mais\" é outra pergunta")
                .isEqualTo(2);
        assertThat(agrupamento.ordem()).isEqualTo(OrdemDosGrupos.QUANTIDADE_DE_PRODUTOS);
    }

    @Test
    void aOrdemPadraoDeveDeclararQueMedeExposicaoENaoGravidade() {
        assertThat(OrdemDosGrupos.padrao().significado())
                .contains("exposição, não gravidade");
        assertThat(OrdemDosGrupos.QUANTIDADE_DE_PRODUTOS.significado())
                .contains("repetição, não valor nem gravidade");
    }

    @Test
    void oValorDoGrupoDeveCarregarRotuloQueImpecaLeituraComoRisco() {
        assertThat(GrupoDeProdutos.ROTULO_DO_VALOR)
                .describedAs("abreviado para \"valor\", ao lado de uma divergência, vira prejuízo")
                .isEqualTo("valor dos produtos envolvidos");
    }

    @Test
    void deveManterOProdutoSemNcmESemClassTribComONivelEscrito() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        GrupoDeProdutos semEnquadramento = agrupamento.grupos().stream()
                .filter(grupo -> grupo.nivel() == NivelDoAgrupamento.SEM_NENHUM_DOS_DOIS)
                .findFirst()
                .orElseThrow();

        assertThat(semEnquadramento.chave().ncm()).isEmpty();
        assertThat(semEnquadramento.chave().cClassTrib()).isEmpty();
        assertThat(semEnquadramento.nivel().rotulo())
                .contains("não declarou NCM nem cClassTrib");
    }

    @Test
    void deveDeclararONivelDeCadaGrupo() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        assertThat(agrupamento.grupos())
                .extracting(GrupoDeProdutos::nivel)
                .describedAs("grupos de níveis diferentes não são comparáveis entre si")
                .contains(NivelDoAgrupamento.NCM_E_CLASSTRIB, NivelDoAgrupamento.SEM_NENHUM_DOS_DOIS);
    }

    @Test
    void todoProdutoDeveCairEmExatamenteUmGrupo() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        int somados = agrupamento.grupos().stream()
                .mapToInt(GrupoDeProdutos::quantidadeDeProdutos)
                .sum();

        assertThat(somados).isEqualTo(produtos().size());
        assertThat(agrupamento.resumo().quantidadeDeProdutos()).isEqualTo(produtos().size());
    }

    @Test
    void deveRecusarAgrupamentoQuePerdeuProdutoPeloCaminho() {
        AgrupamentoDaAnalise completo = agrupar(OrdemDosGrupos.padrao());
        List<GrupoDeProdutos> faltandoUm = completo.grupos().subList(1, completo.grupos().size());

        assertThatThrownBy(() -> new AgrupamentoDaAnalise(
                completo.ordem(), completo.resumo(), faltandoUm))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("some da tela do lote");
    }

    @Test
    void deveEncontrarOGrupoPelaChaveParaATelaAbrirOsProdutosDele() {
        AgrupamentoDaAnalise agrupamento = agrupar(OrdemDosGrupos.padrao());

        ChaveDoGrupo chave = new ChaveDoGrupo(
                java.util.Optional.of(NCM_B),
                java.util.Optional.of(CT_B),
                EstadoDeConferencia.POSSIVEL_DIVERGENCIA);

        assertThat(agrupamento.grupo(chave)).isPresent();
        assertThat(agrupamento.grupo(new ChaveDoGrupo(
                java.util.Optional.of(NCM_B),
                java.util.Optional.of(CT_B),
                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA))).isEmpty();
    }

    private static AgrupamentoDaAnalise agrupar(OrdemDosGrupos ordem) {
        return AgrupamentoDaAnalise.de(produtos(), ordem);
    }

    private static List<ProdutoConferido> produtos() {
        return List.of(
                ConferenciaFicticia.produto(
                        ConferenciaFicticia.chave(),
                        ConferenciaFicticia.item(1, NCM_A, CT_A, "1000.00"),
                        ConferenciaFicticia.produtoCom(EstadoDeConferencia.POSSIVEL_DIVERGENCIA)),
                ConferenciaFicticia.produto(
                        ConferenciaFicticia.outraChave(),
                        ConferenciaFicticia.item(1, NCM_A, CT_A, "500.00"),
                        ConferenciaFicticia.produtoCom(EstadoDeConferencia.POSSIVEL_DIVERGENCIA)),
                ConferenciaFicticia.produto(
                        ConferenciaFicticia.chave(),
                        ConferenciaFicticia.item(2, NCM_B, CT_B, "2000.00"),
                        ConferenciaFicticia.produtoCom(EstadoDeConferencia.POSSIVEL_DIVERGENCIA)),
                ConferenciaFicticia.produto(
                        ConferenciaFicticia.chave(),
                        ConferenciaFicticia.item(3, null, null, "10.00"),
                        ConferenciaFicticia.produtoCom(
                                EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR)),
                ConferenciaFicticia.produto(
                        ConferenciaFicticia.chave(),
                        ConferenciaFicticia.item(4, NCM_A, CT_A, "1.00"),
                        ConferenciaFicticia.produtoCom(
                                EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA)));
    }

    private static GrupoDeProdutos grupoDe(
            AgrupamentoDaAnalise agrupamento, String ncm, String classTrib,
            EstadoDeConferencia situacao) {

        return agrupamento.grupo(new ChaveDoGrupo(
                java.util.Optional.of(ncm), java.util.Optional.of(classTrib), situacao))
                .orElseThrow();
    }
}
