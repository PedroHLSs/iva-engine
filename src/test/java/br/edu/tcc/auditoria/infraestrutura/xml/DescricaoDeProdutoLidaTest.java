package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O xProd é o campo mais sujo do XML, e entra saneado.
 */
@DisplayName("Descrição lida: chave de acesso não entra, branco é ausência")
class DescricaoDeProdutoLidaTest {

    private static final HashDoItem RESUMO = new HashDoItem("a".repeat(64));
    private static final String CHAVE_FICTICIA = "9".repeat(44);

    @Test
    void deveSubstituirCorridaDeQuarentaEQuatroDigitosPeloMarcador() {
        DescricaoDeProdutoLida lida = DescricaoDeProdutoLida.de(
                RESUMO, "TUBO FICTICIO REF NF " + CHAVE_FICTICIA + " PEDIDO 12");

        assertThat(lida.descricao()).isPresent();
        assertThat(lida.descricao().orElseThrow())
                .describedAs("os dígitos do meio da chave são o CNPJ do emitente")
                .doesNotContain(CHAVE_FICTICIA)
                .contains(DescricaoDeProdutoLida.MARCA_DA_CHAVE)
                .contains("TUBO FICTICIO")
                .contains("PEDIDO 12");
    }

    @Test
    void deveSubstituirTodasAsCorridasENaoSoAPrimeira() {
        DescricaoDeProdutoLida lida = DescricaoDeProdutoLida.de(
                RESUMO, CHAVE_FICTICIA + " e " + "8".repeat(44));

        assertThat(lida.descricao().orElseThrow())
                .doesNotContain(CHAVE_FICTICIA)
                .doesNotContain("8".repeat(44));
    }

    @Test
    void deveManterNumeroCurtoQueNaoEChaveDeAcesso() {
        DescricaoDeProdutoLida lida = DescricaoDeProdutoLida.de(RESUMO, "CABO 2,5MM 100M REF 1234");

        assertThat(lida.descricao())
                .describedAs("cortar número curto apagaria a descrição real do produto")
                .contains("CABO 2,5MM 100M REF 1234");
    }

    @Test
    void textoEmBrancoDeveVirarAusencia() {
        assertThat(DescricaoDeProdutoLida.de(RESUMO, "   ").descricao()).isEmpty();
        assertThat(DescricaoDeProdutoLida.de(RESUMO, "").descricao()).isEmpty();
        assertThat(DescricaoDeProdutoLida.de(RESUMO, null).descricao()).isEmpty();
    }

    @Test
    void deveExigirOResumoDoItemComoEndereco() {
        assertThatThrownBy(() -> DescricaoDeProdutoLida.de(null, "PRODUTO FICTICIO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumo do item");
    }

    @Test
    void deveRecusarDescricaoNula() {
        assertThatThrownBy(() -> new DescricaoDeProdutoLida(RESUMO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void deveRemoverEspacoEmVoltaSemMexerNoMeio() {
        assertThat(DescricaoDeProdutoLida.de(RESUMO, "  CANO PVC  50MM  ").descricao())
                .contains("CANO PVC  50MM");
    }

    @Test
    void oColetorDeveDistinguirNaoLidoDeLidoSemDescricao() {
        DescricoesDeProdutoEmMemoria coletor = new DescricoesDeProdutoEmMemoria();
        HashDoItem outro = new HashDoItem("b".repeat(64));

        coletor.registrar(DescricaoDeProdutoLida.de(RESUMO, null));

        assertThat(coletor.de(RESUMO))
                .describedAs("passou pela leitura, e o documento não declarou nada")
                .contains(Optional.empty());
        assertThat(coletor.de(outro))
                .describedAs("não passou por esta leitura, que é outra coisa")
                .isEmpty();
    }

    @Test
    void doisColetoresNaoDevemEnxergarUmAoOutro() {
        DescricoesDeProdutoEmMemoria primeira = new DescricoesDeProdutoEmMemoria();
        DescricoesDeProdutoEmMemoria segunda = new DescricoesDeProdutoEmMemoria();

        primeira.registrar(DescricaoDeProdutoLida.de(RESUMO, "PRODUTO DA PRIMEIRA ANALISE"));

        assertThat(primeira.quantidade()).isEqualTo(1);
        assertThat(segunda.vazio())
                .describedAs("o acumulador é da análise, não do processo")
                .isTrue();
        assertThat(segunda.de(RESUMO)).isEmpty();
    }

    @Test
    void oSumidouroNaoDeveGuardarNada() {
        RegistroDeDescricoesDeProduto.DESCARTA.registrar(
                DescricaoDeProdutoLida.de(RESUMO, "PRODUTO FICTICIO"));

        // Não há o que afirmar sobre o conteúdo: é esse o ponto. O teste existe
        // para que o sumidouro continue aceitando registro sem explodir, que é o
        // contrato dele na linha de comando.
        assertThat(RegistroDeDescricoesDeProduto.DESCARTA).isNotNull();
    }
}
