package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

// Representa um item de uma análise, com sua chave de acesso, número, hash e descrição do produto.
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
