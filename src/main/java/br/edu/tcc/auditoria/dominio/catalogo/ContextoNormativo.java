package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.util.List;
import java.util.Optional;

/**
 * O catálogo normativo já resolvido numa data, do ponto de vista de quem escreve
 * regra de auditoria.
 *
 * <p><strong>Nenhum método desta interface aceita data.</strong> Isso não é
 * economia de parâmetro: é a garantia estrutural de que uma regra não consegue
 * escolher em que data consultar o catálogo. A data é fixada uma vez, na
 * construção da implementação, a partir da emissão do documento auditado, e a
 * regra não tem como sobrepô-la — nem por descuido, nem chamando
 * {@code LocalDate.now()}, porque não há onde passar o resultado.</p>
 *
 * <p>Consultar o catálogo "hoje" para auditar um documento emitido no ano
 * passado produziria apontamento fundamentado em norma que não valia quando o
 * documento foi emitido. Num relatório de auditoria, isso é um erro grave e
 * silencioso — o texto sai plausível e está errado.</p>
 *
 * <p>A interface também não devolve a data de referência. Uma regra que precise
 * registrar a vigência aplicada num {@code Achado} deve tomá-la do próprio
 * registro consultado, via {@code RegistroNormativo.vigencia()}: é a vigência
 * que de fato sustentou o apontamento, e não uma data solta que a regra teria
 * de correlacionar por conta própria.</p>
 *
 * <p>Toda consulta devolve vazio quando o catálogo nada diz — código
 * inexistente, ou data fora de toda vigência registrada. Vazio nunca significa
 * conformidade.</p>
 */
public interface ContextoNormativo {

    /** O que o catálogo diz sobre o código, na data de referência. */
    Optional<ClassificacaoTributaria> classificacaoTributaria(CodigoClassificacaoTributaria codigo);

    /** O que o catálogo diz sobre o NCM, na data de referência. */
    Optional<RegistroNcm> registroNcm(Ncm ncm);

    /** Anexos a que o NCM estava vinculado na data de referência. */
    List<ItemAnexo> anexosDoNcm(Ncm ncm);

    /** Alíquota do par tributo e abrangência, na data de referência. */
    Optional<AliquotaVigente> aliquota(Tributo tributo, Abrangencia abrangencia);

    /** Alíquotas do tributo em todas as abrangências, na data de referência. */
    List<AliquotaVigente> aliquotas(Tributo tributo);
}
