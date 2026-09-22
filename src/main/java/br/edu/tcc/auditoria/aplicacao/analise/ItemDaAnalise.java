package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

/**
 * Um item que uma análise leu — com apontamento, sem apontamento ou sem
 * conclusão.
 *
 * <p>É o que permite perguntar "quais são os produtos desta análise" sem derivar
 * a resposta dos apontamentos, o que devolveria só os produtos com problema e
 * faria uma nota inteiramente conforme sumir do resultado da análise que a
 * leu.</p>
 *
 * <p>O {@link HashDoItem} é o que <strong>esta</strong> análise leu. O item
 * gravado é sobrescrito a cada reprocessamento, então a divergência entre os dois
 * é o sinal de que o conteúdo mudou depois — e o sinal é para ser dito, não
 * corrigido em silêncio.</p>
 */
public record ItemDaAnalise(
        ChaveAcesso chaveAcesso,
        int numeroItem,
        HashDoItem hashDoItem,
        DescricaoDoProduto descricao) {

    public ItemDaAnalise {
        if (chaveAcesso == null) {
            throw new AnaliseInvalida("O item da análise precisa da chave de acesso do documento.");
        }
        if (hashDoItem == null) {
            throw new AnaliseInvalida(
                    "O item da análise precisa do resumo do conteúdo que foi lido: sem ele não há como "
                            + "perceber que o item mudou depois.");
        }
        if (numeroItem < 1) {
            throw new AnaliseInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        if (descricao == null) {
            throw new AnaliseInvalida(
                    "O item da análise precisa da descrição do produto, ainda que ela seja o motivo de "
                            + "não haver uma. Nulo aqui viraria célula em branco na tela.");
        }
    }
}
