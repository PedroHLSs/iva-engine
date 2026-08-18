package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

/**
 * Até onde vai o que o catálogo carregado é capaz de responder.
 *
 * <h2>O problema que este tipo resolve</h2>
 *
 * <p>{@code ContextoNormativo} devolve vazio em duas situações que, do lado de
 * dentro, são indistinguíveis:</p>
 *
 * <ol>
 *   <li>a tabela foi carregada e o código realmente não consta dela — o que é
 *       exatamente o achado que R01 e R06 existem para produzir;</li>
 *   <li>a tabela não foi carregada, ou foi carregada com vigências que não
 *       alcançam a data do documento — caso em que apontar seria acusar sem
 *       base.</li>
 * </ol>
 *
 * <p>Sem separar as duas, uma carga incompleta viraria uma enxurrada de
 * apontamentos falsos, todos com aparência de conclusão firme. Do outro lado, se
 * a regra desistisse sempre que o catálogo devolvesse vazio, R01 e R06 nunca
 * apontariam nada e o sistema perderia a verificação mais elementar que tem.</p>
 *
 * <h2>Como se resolve</h2>
 *
 * <p>Quem carrega o catálogo declara, por tabela, a vigência que aquela carga
 * cobre e a fonte de onde ela saiu. Dentro da cobertura, silêncio do catálogo é
 * resposta e vira apontamento. Fora da cobertura, silêncio é ausência de dado e
 * vira {@code NAO_AVALIADO} com motivo.</p>
 *
 * <p>Os valores não são escritos em código: chegam de fora, junto com os CSVs a
 * que se referem, exatamente como o resto do conteúdo normativo. A cobertura
 * também é o que dá fundamento e vigência ao apontamento de "não consta": nesse
 * caso não há registro consultado de onde tomá-los, porque a inexistência do
 * registro é o próprio achado.</p>
 *
 * @param classificacoesTributarias cobertura da tabela de {@code cClassTrib}
 * @param ncm                       cobertura da tabela de NCM
 * @param itensDeAnexo              cobertura da tabela de vínculo entre NCM e anexo
 */
public record CoberturaDoCatalogo(
        ProcedenciaNormativa classificacoesTributarias,
        ProcedenciaNormativa ncm,
        ProcedenciaNormativa itensDeAnexo) {

    public CoberturaDoCatalogo {
        exigir(classificacoesTributarias, "classificações tributárias");
        exigir(ncm, "NCM");
        exigir(itensDeAnexo, "itens de anexo");
    }

    private static void exigir(ProcedenciaNormativa cobertura, String tabela) {
        if (cobertura == null) {
            throw new RegraInvalida(
                    "A cobertura da tabela de %s não foi declarada.".formatted(tabela));
        }
    }
}
