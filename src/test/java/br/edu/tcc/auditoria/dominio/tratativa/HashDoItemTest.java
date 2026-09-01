package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;
import br.edu.tcc.auditoria.dominio.regras.CenarioFicticio;
import br.edu.tcc.auditoria.dominio.regras.ConstrutorDeItem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O resumo do item é a identidade que faz a tratativa sobreviver ao
 * reprocessamento. Estes testes fixam as propriedades das quais isso depende.
 */
class HashDoItemTest {

    private static final ChaveAcesso PRIMEIRA = new ChaveAcesso(CenarioFicticio.CHAVE_PRIMEIRA);
    private static final ChaveAcesso SEGUNDA = new ChaveAcesso(CenarioFicticio.CHAVE_SEGUNDA);

    @Test
    void deveProduzirOMesmoResumoParaOMesmoItemDoMesmoDocumento() {
        assertThat(HashDoItem.de(PRIMEIRA, itemCompleto()))
                .as("reprocessar o mesmo arquivo precisa reencontrar a mesma tratativa")
                .isEqualTo(HashDoItem.de(PRIMEIRA, itemCompleto()));
    }

    @Test
    void deveDistinguirOMesmoItemEmDocumentosDiferentes() {
        assertThat(HashDoItem.de(PRIMEIRA, itemCompleto()))
                .as("o item 1 existe em todo documento; o resumo precisa dizer de qual")
                .isNotEqualTo(HashDoItem.de(SEGUNDA, itemCompleto()));
    }

    @Test
    void deveDistinguirItensDeNumerosDiferentesNoMesmoDocumento() {
        ItemDocumento primeiro = ConstrutorDeItem.item().numero(1).construir();
        ItemDocumento segundo = ConstrutorDeItem.item().numero(2).construir();

        assertThat(HashDoItem.de(PRIMEIRA, primeiro)).isNotEqualTo(HashDoItem.de(PRIMEIRA, segundo));
    }

    @Test
    void deveMudarQuandoQualquerCampoDeclaradoMuda() {
        ItemDocumento original = itemCompleto();
        ItemDocumento comOutroValor = ConstrutorDeItem.item()
                .numero(1)
                .ncm(CenarioFicticio.NCM)
                .cstIbs(CenarioFicticio.CST)
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("11.11")
                .construir();

        assertThat(HashDoItem.de(PRIMEIRA, original))
                .as("item com conteúdo diferente é pergunta nova, e o apontamento reabre")
                .isNotEqualTo(HashDoItem.de(PRIMEIRA, comOutroValor));
    }

    @Test
    void deveDistinguirCampoAusenteDeCampoComZero() {
        ItemDocumento semBase = ConstrutorDeItem.item().numero(1).construir();
        ItemDocumento comBaseZero = ConstrutorDeItem.item().numero(1).baseCalculoIbs("0").construir();

        assertThat(HashDoItem.de(PRIMEIRA, semBase))
                .as("omitir a base e declarar base zero são fatos fiscais distintos")
                .isNotEqualTo(HashDoItem.de(PRIMEIRA, comBaseZero));
    }

    @Test
    void deveDistinguirEscalasDiferentesDoMesmoNumero() {
        ItemDocumento semCasas = ConstrutorDeItem.item().numero(1).baseCalculoIbs("0").construir();
        ItemDocumento comDuasCasas = ConstrutorDeItem.item().numero(1).baseCalculoIbs("0.00").construir();

        assertThat(HashDoItem.de(PRIMEIRA, semCasas))
                .as("para a auditoria, \"0\" e \"0,00\" não são o mesmo registro")
                .isNotEqualTo(HashDoItem.de(PRIMEIRA, comDuasCasas));
    }

    @Test
    void deveTerSessentaEQuatroCaracteresHexadecimaisMinusculos() {
        assertThat(HashDoItem.de(PRIMEIRA, itemCompleto()).valor())
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void deveRecusarResumoForaDoFormato() {
        assertThatThrownBy(() -> new HashDoItem("ABC"))
                .isInstanceOf(TratativaInvalida.class);
        assertThatThrownBy(() -> new HashDoItem("A".repeat(64)))
                .as("hexadecimal maiúsculo produziria duas grafias da mesma identidade")
                .isInstanceOf(TratativaInvalida.class);
    }

    @Test
    void deveExigirChaveDeAcessoEItem() {
        assertThatThrownBy(() -> HashDoItem.de(null, itemCompleto()))
                .isInstanceOf(TratativaInvalida.class);
        assertThatThrownBy(() -> HashDoItem.de(PRIMEIRA, null))
                .isInstanceOf(TratativaInvalida.class);
    }

    private static ItemDocumento itemCompleto() {
        return ConstrutorDeItem.item()
                .numero(1)
                .ncm(CenarioFicticio.NCM)
                .cfop("9999")
                .valorItem("99.99")
                .cstIbs(CenarioFicticio.CST)
                .cstCbs(CenarioFicticio.CST)
                .classificacao(CenarioFicticio.CODIGO)
                .baseCalculoIbs("99.99")
                .baseCalculoCbs("99.99")
                .valorIbsUf("9.99")
                .construir();
    }
}
