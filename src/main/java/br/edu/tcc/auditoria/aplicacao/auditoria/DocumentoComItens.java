package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Um documento junto dos seus itens, pronto para ser auditado.
 *
 * <p>O domínio mantém {@code Documento} e {@code ItemDocumento} separados de
 * propósito, e atribui à aplicação a tarefa de compô-los. Este é o tipo dessa
 * composição, e é por isso que ele mora aqui e não no domínio.</p>
 *
 * <p>Lista de itens vazia é aceita. Documento fiscal sem item não deveria
 * existir, mas afirmar isso em código seria afirmar leiaute; o que o sistema faz
 * com um documento assim é não produzir avaliação de item nenhum, o que é
 * visível no relatório.</p>
 */
public record DocumentoComItens(Documento documento, List<ItemDocumento> itens) {

    public DocumentoComItens {
        if (documento == null) {
            throw new AuditoriaInvalida("Não há documento a auditar.");
        }
        if (itens == null) {
            throw new AuditoriaInvalida(
                    "A lista de itens deve ser vazia quando o documento não tem item, nunca nula.");
        }
        if (itens.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de itens não pode conter elemento nulo.");
        }
        itens = List.copyOf(itens);
    }

    /**
     * Os itens em ordem de número de item.
     *
     * <p>A ordenação é estável, de modo que dois itens com o mesmo número — que
     * o modelo não proíbe, por não afirmar leiaute — mantêm entre si a ordem em
     * que chegaram.</p>
     */
    public List<ItemDocumento> itensOrdenados() {
        return itens.stream()
                .sorted(Comparator.comparingInt(ItemDocumento::numeroItem))
                .toList();
    }
}
