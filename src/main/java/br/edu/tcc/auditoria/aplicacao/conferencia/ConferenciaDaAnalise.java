package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma análise inteira, no vocabulário de quem confere.
 *
 * <p>A identificação da execução vem junta, e não é enfeite: sem saber contra
 * qual catálogo e qual conjunto de regras o resultado foi produzido, um
 * apontamento que deixou de proceder por mudança de tabela fica indistinguível
 * de um erro do sistema. É a mesma razão pela qual a identificação abre o Resumo
 * do papel de trabalho (D007).</p>
 *
 * <p>Os arquivos ilegíveis andam ao lado do resumo, nunca dentro dele — ver
 * {@link ResumoDaConferencia}.</p>
 */
public record ConferenciaDaAnalise(
        ExecucaoAuditoria execucao,
        ResumoDaConferencia resumo,
        List<ProdutoConferido> produtos,
        List<ArquivoIlegivel> arquivosIlegiveis) {

    public ConferenciaDaAnalise {
        if (execucao == null) {
            throw new ConferenciaInvalida(
                    "A conferência precisa do recibo da execução: sem ele o resultado não diz contra "
                            + "o que foi produzido.");
        }
        if (resumo == null) {
            throw new ConferenciaInvalida("A conferência precisa do resumo.");
        }
        if (produtos == null || arquivosIlegiveis == null) {
            throw new ConferenciaInvalida(
                    "As listas de produtos e de arquivos ilegíveis devem ser vazias quando não há "
                            + "nenhum, nunca nulas.");
        }
        if (produtos.stream().anyMatch(Objects::isNull)
                || arquivosIlegiveis.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("Nenhuma das duas listas pode conter elemento nulo.");
        }
        if (resumo.quantidadeDeProdutos() != produtos.size()) {
            throw new ConferenciaInvalida(
                    ("O resumo conta %d produto(s) e a lista traz %d. Uma resposta internamente "
                            + "contraditória é pior que nenhuma.")
                            .formatted(resumo.quantidadeDeProdutos(), produtos.size()));
        }
        produtos = List.copyOf(produtos);
        arquivosIlegiveis = List.copyOf(arquivosIlegiveis);
    }

    public int quantidadeDeArquivosIlegiveis() {
        return arquivosIlegiveis.size();
    }

    /**
     * Quantas notas distintas esta análise leu.
     *
     * <p>Decide qual tela abre: uma nota é a tela da nota, várias é a tela do
     * lote. Um pacote com um documento só continua sendo uma nota, e é assim que
     * tem de aparecer.</p>
     */
    public int quantidadeDeNotas() {
        return execucao.quantidadeDocumentos();
    }

    /** O produto de endereço dado, se ele for desta análise. */
    public Optional<ProdutoConferido> produto(String endereco) {
        if (endereco == null || endereco.isBlank()) {
            throw new ConferenciaInvalida("Não há endereço de produto a procurar.");
        }
        return produtos.stream()
                .filter(produto -> produto.endereco().equals(endereco))
                .findFirst();
    }
}
