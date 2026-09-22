package br.edu.tcc.auditoria.aplicacao.consulta;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

/**
 * Um item gravado, com os dois resumos que dizem se ele ainda é o que a análise
 * leu.
 *
 * <h2>Os dois hashes, e por que não basta um</h2>
 *
 * <p>{@code hashLidoNaAnalise} vem de {@code item_da_execucao} e é o conteúdo
 * que <strong>aquela</strong> análise viu. {@code hashAtual} vem de
 * {@code item_documento}, que é sobrescrito a cada reprocessamento (D006).</p>
 *
 * <p>Enquanto ninguém reprocessa, os dois coincidem. Quando divergem, a mesma
 * nota foi enviada de novo com conteúdo diferente depois desta análise — e a
 * tela precisa dizer isso, em vez de mostrar calada o valor novo sob a data
 * antiga. {@link #foiReprocessadoDepois()} é essa pergunta.</p>
 */
public record DadosDoItem(
        ChaveAcesso chaveAcesso,
        ItemDocumento item,
        DescricaoDoProduto descricao,
        HashDoItem hashLidoNaAnalise,
        HashDoItem hashAtual) {

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

    /**
     * Se o conteúdo gravado deixou de ser o que esta análise leu.
     *
     * <p>Quando responde {@code true}, os campos deste item são os de agora, e
     * não os daquela análise — a Etapa 5 não guarda versões do item, guarda o
     * item. A tela mostra os campos assim mesmo e avisa, porque esconder a linha
     * faria o produto sumir do resultado que o contém.</p>
     */
    public boolean foiReprocessadoDepois() {
        return !hashLidoNaAnalise.equals(hashAtual);
    }
}
