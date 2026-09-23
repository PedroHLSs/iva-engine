package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

// Representa um item gravado com dois resumos, o que a análise leu e o atual, para saber se o item foi reprocessado depois.
public record DadosDoItem(
        ChaveAcesso chaveAcesso,
        ItemDocumento item,
        DescricaoDoProduto descricao,
        HashDoItem hashLidoNaAnalise,
        HashDoItem hashAtual) {

    // Valida que o item tenha chave de acesso, conteúdo, descrição do produto e os dois resumos.
    public DadosDoItem {
        if (chaveAcesso == null) {
            throw new ConsultaInvalida("O item gravado precisa da chave de acesso do documento.");
        }
        if (item == null) {
            throw new ConsultaInvalida("Não há item a apresentar.");
        }
        if (descricao == null) {
            throw new ConsultaInvalida(
                    "O item precisa da descrição do produto, ainda que ela seja o motivo de não haver "
                            + "uma. Ela vem da análise, e não de item_documento: a descrição que "
                            + "interessa é a que foi lida junto do NCM que a análise viu.");
        }
        if (hashLidoNaAnalise == null || hashAtual == null) {
            throw new ConsultaInvalida(
                    "O item precisa do resumo que a análise leu e do resumo atual: é a divergência "
                            + "entre os dois que denuncia reprocessamento posterior.");
        }
    }

    public int numeroItem() {
        return item.numeroItem();
    }

    // Indica se o conteúdo gravado deixou de ser o que esta análise leu, ou seja, se a nota foi reprocessada depois.
    public boolean foiReprocessadoDepois() {
        return !hashLidoNaAnalise.equals(hashAtual);
    }
}
