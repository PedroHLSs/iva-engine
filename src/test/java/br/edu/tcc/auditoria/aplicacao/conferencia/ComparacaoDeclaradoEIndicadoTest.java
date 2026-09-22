package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O quadro põe os dois lados lado a lado e não emite veredito próprio.
 */
@DisplayName("Declarado e indicado: comparação sem segundo veredito")
class ComparacaoDeclaradoEIndicadoTest {

    private static final LocalDate DATA = LocalDate.of(1900, 6, 15);
    private static final String VERSAO = "carga-ficticia-de-teste";

    /**
     * Palavras que, num campo do quadro, significariam juízo sobre a norma.
     *
     * <p>Quem julga são as sete regras, e o julgamento delas está na situação do
     * produto. Um veredito aqui seria um oitavo juízo, mais fraco: ignoraria
     * tolerância, cobertura declarada e as condições que cada regra examina.</p>
     */
    private static final List<String> PALAVRAS_DE_VEREDITO = List.of(
            "confere", "conforme", "divergen", "igual", "diferente", "situacao", "resultado", "ok");

    @Test
    void deveTrazerAsCincoLinhasEmQueOsDoisLadosFalamDoMesmoCampo() {
        ComparacaoDeclaradoEIndicado comparacao = comparar(itemSemNadaDeclarado());

        assertThat(comparacao.linhas())
                .extracting(ComparacaoDeclaradoEIndicado.Linha::campo)
                .containsExactly(
                        "CST do IBS",
                        "CST da CBS",
                        "Alíquota do IBS - parcela estadual",
                        "Alíquota do IBS - parcela municipal",
                        "Alíquota da CBS");
    }

    @Test
    void nenhumCampoDaLinhaPodeSerUmVeredito() {
        RecordComponent[] componentes =
                ComparacaoDeclaradoEIndicado.Linha.class.getRecordComponents();

        assertThat(componentes)
                .describedAs("autoverificação: a varredura precisa ter olhado alguma coisa")
                .hasSizeGreaterThanOrEqualTo(4);

        List<String> nomes = Arrays.stream(componentes)
                .map(componente -> componente.getName().toLowerCase(Locale.ROOT))
                .toList();

        for (String nome : nomes) {
            assertThat(PALAVRAS_DE_VEREDITO)
                    .describedAs("o campo \"%s\" parece um veredito, e o quadro não emite veredito",
                            nome)
                    .noneMatch(nome::contains);
        }
    }

    @Test
    void deveDizerQueOJulgamentoEDasRegras() {
        ComparacaoDeclaradoEIndicado comparacao = comparar(itemSemNadaDeclarado());

        assertThat(comparacao.linhas()).isNotEmpty();
        assertThat(ComparacaoDeclaradoEIndicado.QUEM_JULGA)
                .contains("A conferência das regras sobre este produto está na situação acima");
    }

    @Test
    void ladoDeclaradoVazioDeveVirComOMotivoENuncaEmBranco() {
        ComparacaoDeclaradoEIndicado comparacao = comparar(itemSemNadaDeclarado());

        assertThat(comparacao.linhas()).allSatisfy(linha -> {
            assertThat(linha.declarado()).isEmpty();
            assertThat(linha.motivoDoNaoDeclarado())
                    .describedAs("célula em branco numa comparação é lida como igualdade")
                    .isPresent();
            assertThat(linha.motivoDoNaoDeclarado().orElseThrow()).isNotBlank();
        });
    }

    @Test
    void ladoIndicadoVazioDeveVirComOMotivoDoCatalogo() {
        ComparacaoDeclaradoEIndicado comparacao = comparar(itemSemNadaDeclarado());

        assertThat(comparacao.linhas()).allSatisfy(linha ->
                assertThat(linha.indicado().motivoDaAusencia())
                        .describedAs("sem carga, os dois lados dizem por que estão vazios")
                        .isPresent());
    }

    private static ComparacaoDeclaradoEIndicado comparar(ItemDocumento item) {
        TratamentoIdentificado tratamento = TratamentoIdentificado.naoDeterminado(
                VERSAO, DATA, "motivo fictício de teste: a carga não foi montada neste cenário");

        assertThat(tratamento.porTributo())
                .describedAs("o cenário depende de os três tributos existirem")
                .hasSize(Tributo.values().length);

        return ComparacaoDeclaradoEIndicado.de(item, tratamento);
    }

    private static ItemDocumento itemSemNadaDeclarado() {
        return ConferenciaFicticia.item(1, null, null, "10.00");
    }
}
