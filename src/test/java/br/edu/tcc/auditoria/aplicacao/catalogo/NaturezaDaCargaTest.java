package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A procedência é derivada das tabelas, e o caso misto é o que importa.
 */
@DisplayName("Natureza da carga: quatro situações, e nenhuma suposta")
class NaturezaDaCargaTest {

    @Test
    void tudoFicticioDeveSerInteiramenteFicticio() {
        NaturezaDaCarga natureza = NaturezaDaCarga.deUmaSoProcedencia(
                Natureza.FICTICIO, List.of("a"), List.of("b"), List.of("c"), List.of("d"));

        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.INTEIRAMENTE_FICTICIO);
        assertThat(natureza.situacao().exigeAviso()).isTrue();
        assertThat(natureza.tabelasFicticias()).hasSize(4);
    }

    @Test
    void tudoNormativoNaoDeveExigirAviso() {
        NaturezaDaCarga natureza = NaturezaDaCarga.deUmaSoProcedencia(
                Natureza.NORMATIVO, List.of("a"), List.of("b"), List.of("c"), List.of("d"));

        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.NORMATIVO);
        assertThat(natureza.situacao().exigeAviso()).isFalse();
        assertThat(natureza.tabelasFicticias()).isEmpty();
    }

    /**
     * O caso que motivou guardar por tabela.
     *
     * <p>Um anexo real com o resto fictício. Um sinalizador único teria de
     * escolher entre chamar a carga de real ou de fictícia, e as duas respostas
     * estariam erradas.</p>
     */
    @Test
    void anexoRealComORestoFicticioDeveSerParcialEListarAsTabelas() {
        NaturezaDaCarga natureza = new NaturezaDaCarga(
                Optional.of(Natureza.FICTICIO),
                Optional.of(Natureza.FICTICIO),
                Optional.of(Natureza.NORMATIVO),
                Optional.of(Natureza.FICTICIO));

        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.PARCIALMENTE_FICTICIO);
        assertThat(natureza.tabelasFicticias())
                .describedAs("quem lê precisa saber em que parte da tela pode confiar")
                .containsExactly(
                        NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS,
                        NaturezaDaCarga.REGISTROS_DE_NCM,
                        NaturezaDaCarga.ALIQUOTAS)
                .doesNotContain(NaturezaDaCarga.ITENS_DE_ANEXO);
    }

    @Test
    void cargaAnteriorDeveSerNaoDeclaradaENuncaNormativa() {
        NaturezaDaCarga natureza = NaturezaDaCarga.naoDeclarada();

        assertThat(natureza.situacao())
                .describedAs("supor normativo seria afirmar que dado de origem desconhecida é lei")
                .isEqualTo(SituacaoDaNatureza.NAO_DECLARADA)
                .isNotEqualTo(SituacaoDaNatureza.NORMATIVO);
        assertThat(natureza.situacao().exigeAviso()).isTrue();
        assertThat(natureza.declaradas()).isEmpty();
    }

    @Test
    void aExplicacaoDeCadaSituacaoDeveDizerOQueAFaixaQuerDizer() {
        for (SituacaoDaNatureza situacao : SituacaoDaNatureza.values()) {
            assertThat(situacao.rotulo()).describedAs(situacao.name()).isNotBlank();
            assertThat(situacao.explicacao()).describedAs(situacao.name()).isNotBlank();
        }
        assertThat(SituacaoDaNatureza.NAO_DECLARADA.explicacao())
                .contains("Não se supõe que seja normativo");
        assertThat(SituacaoDaNatureza.PARCIALMENTE_FICTICIO.explicacao())
                .contains("vêm listadas");
    }

    @Test
    void tabelaVaziaNaoDeveTerNatureza() {
        NaturezaDaCarga natureza = NaturezaDaCarga.deUmaSoProcedencia(
                Natureza.FICTICIO, List.of("a"), List.of(), List.of(), List.of());

        assertThat(natureza.registrosDeNcm())
                .describedAs("arquivo só com cabeçalho não tem linha onde declarar procedência")
                .isEmpty();
        assertThat(natureza.declaradas())
                .containsOnlyKeys(NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS);
        assertThat(natureza.situacao()).isEqualTo(SituacaoDaNatureza.INTEIRAMENTE_FICTICIO);
    }

    @Test
    void deveRecusarNaturezaNula() {
        assertThatThrownBy(() -> new NaturezaDaCarga(
                null, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void osRotulosDaNaturezaNaoPodemSerVazios() {
        for (Natureza natureza : Natureza.values()) {
            assertThat(natureza.rotulo()).describedAs(natureza.name()).isNotBlank();
        }
    }
}
