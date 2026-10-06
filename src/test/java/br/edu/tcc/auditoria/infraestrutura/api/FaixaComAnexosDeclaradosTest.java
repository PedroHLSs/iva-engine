package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tabela de anexos declarados (ANEXO_DECLARADO) na faixa de procedência,
 * decisão do usuário de 01/10/2026: entra em {@code porTabela} e no cálculo da
 * situação como as outras quatro, e carga sem ela continua exatamente como era.
 */
class FaixaComAnexosDeclaradosTest {

    private static final Optional<Natureza> NORMATIVO = Optional.of(Natureza.NORMATIVO);

    @Test
    void cargaSemATabelaNovaDeveContinuarComQuatroTabelasEAMesmaSituacao() {
        // D021 (04/10/2026): a cobertura passou a declarar natureza, e carga sem ela é procedência não declarada —
        // ver CoberturaNaNaturezaTest. Para continuar medindo só a ausência da tabela de anexos, a cobertura vem declarada.
        FaixaDeNatureza faixa = FaixaDeNatureza.de(
                new NaturezaDaCarga(NORMATIVO, NORMATIVO, NORMATIVO, NORMATIVO, Optional.empty(), NORMATIVO),
                "carga-ficticia");

        assertThat(faixa.porTabela()).hasSize(5);
        assertThat(faixa.porTabela()).extracting(FaixaDeNatureza.TabelaExposta::tabela)
                .containsExactly("CLASSIFICACAO_TRIBUTARIA", "NCM", "ITEM_ANEXO", "ALIQUOTA", "COBERTURA");
        assertThat(faixa.situacao()).isEqualTo("NORMATIVO");
        assertThat(faixa.tabelasFicticias()).isEmpty();
    }

    @Test
    void aTabelaNovaDeveEntrarNaFaixaENaSituacao() {
        FaixaDeNatureza faixa = FaixaDeNatureza.de(
                new NaturezaDaCarga(NORMATIVO, NORMATIVO, NORMATIVO, NORMATIVO, Optional.of(Natureza.FICTICIO)),
                "carga-ficticia");

        assertThat(faixa.porTabela()).hasSize(5);
        assertThat(faixa.porTabela()).extracting(FaixaDeNatureza.TabelaExposta::tabela)
                .endsWith("ANEXO_DECLARADO");
        assertThat(faixa.situacao())
                .describedAs("quatro tabelas normativas e uma de demonstração é carga parcialmente fictícia")
                .isEqualTo("PARCIALMENTE_FICTICIO");
        assertThat(faixa.tabelasFicticias()).containsExactly("ANEXO_DECLARADO");
    }
}
