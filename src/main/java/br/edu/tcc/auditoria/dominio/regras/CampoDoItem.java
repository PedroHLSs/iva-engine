package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.ItemDocumento;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vocabulário pelo qual o catálogo se refere aos campos de um item.
 *
 * <p>O catálogo diz, por {@code cClassTrib}, quais campos passam a ser exigidos.
 * Ele diz isso por nome, em texto, dentro do CSV. Para que R07 consiga conferir,
 * é preciso haver um vocabulário publicado, e este enum é ele: o nome aceito é
 * exatamente o do componente correspondente em {@link ItemDocumento}.</p>
 *
 * <p>Usar os nomes do próprio modelo, e não uma lista inventada aqui, tem duas
 * consequências boas. A primeira é que não há tradução a manter. A segunda é que
 * há teste por reflexão garantindo que cada nome ainda existe em
 * {@code ItemDocumento} — se um campo for renomeado, o teste falha em vez de a
 * regra passar a ignorar silenciosamente uma exigência do catálogo.</p>
 *
 * <p>A comparação de nome é exata. Aparar espaço e normalizar caixa é trabalho
 * da importação, não do domínio, e adivinhar o que o operador quis dizer seria
 * pior do que recusar: nome desconhecido faz R07 devolver {@code NAO_AVALIADO}
 * citando o nome, que é acionável.</p>
 *
 * <p>{@code valorItem} não está aqui de propósito: não é {@code Optional} no
 * modelo, sempre existe, e não faria sentido o catálogo exigi-lo.</p>
 */
public enum CampoDoItem {

    NCM("ncm", ItemDocumento::ncm),
    CFOP("cfop", ItemDocumento::cfop),
    CST_IBS("cstIbs", ItemDocumento::cstIbs),
    CST_CBS("cstCbs", ItemDocumento::cstCbs),
    CODIGO_CLASSIFICACAO_TRIBUTARIA(
            "codigoClassificacaoTributaria", ItemDocumento::codigoClassificacaoTributaria),
    BASE_CALCULO_IBS("baseCalculoIbs", ItemDocumento::baseCalculoIbs),
    BASE_CALCULO_CBS("baseCalculoCbs", ItemDocumento::baseCalculoCbs),
    ALIQUOTA_IBS_UF("aliquotaIbsUf", ItemDocumento::aliquotaIbsUf),
    ALIQUOTA_IBS_MUNICIPAL("aliquotaIbsMunicipal", ItemDocumento::aliquotaIbsMunicipal),
    ALIQUOTA_CBS("aliquotaCbs", ItemDocumento::aliquotaCbs),
    VALOR_IBS_UF("valorIbsUf", ItemDocumento::valorIbsUf),
    VALOR_IBS_MUNICIPAL("valorIbsMunicipal", ItemDocumento::valorIbsMunicipal),
    VALOR_CBS("valorCbs", ItemDocumento::valorCbs);

    private final String nomeNoCatalogo;
    private final Function<ItemDocumento, Optional<?>> leitura;

    CampoDoItem(String nomeNoCatalogo, Function<ItemDocumento, Optional<?>> leitura) {
        this.nomeNoCatalogo = nomeNoCatalogo;
        this.leitura = leitura;
    }

    /** Nome pelo qual o CSV do catálogo se refere a este campo. */
    public String nomeNoCatalogo() {
        return nomeNoCatalogo;
    }

    /** O campo reconhecido por este nome, vazio se o vocabulário não o conhece. */
    public static Optional<CampoDoItem> porNome(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(campo -> campo.nomeNoCatalogo.equals(nome))
                .findFirst();
    }

    /**
     * Indica se o item trouxe este campo.
     *
     * <p>Preenchido é ter vindo, não ser diferente de zero: um campo declarado
     * com valor zero foi preenchido, e a distinção entre ausência e zero é o
     * eixo do modelo.</p>
     */
    public boolean estaPreenchidoEm(ItemDocumento item) {
        return leitura.apply(item).isPresent();
    }
}
