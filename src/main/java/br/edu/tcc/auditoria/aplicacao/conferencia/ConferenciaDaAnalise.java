package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

// Representa uma análise inteira no vocabulário de quem confere, com a execução, o resumo, os produtos e os arquivos ilegíveis.
// Emenda de 04/10/2026 (D018): os ilegíveis vêm vazios quando a leitura da execução não foi registrada. Lista vazia passou a querer dizer só "nenhum falhou".
public record ConferenciaDaAnalise(
        ExecucaoAuditoria execucao,
        ResumoDaConferencia resumo,
        List<ProdutoConferido> produtos,
        Optional<List<ArquivoIlegivel>> arquivosIlegiveis) {

    // Valida a conferência e confere que o resumo conta o mesmo número de produtos da lista.
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
                || arquivosIlegiveis.orElse(List.of()).stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("Nenhuma das duas listas pode conter elemento nulo.");
        }
        if (resumo.quantidadeDeProdutos() != produtos.size()) {
            throw new ConferenciaInvalida(
                    ("O resumo conta %d produto(s) e a lista traz %d. Uma resposta internamente "
                            + "contraditória é pior que nenhuma.")
                            .formatted(resumo.quantidadeDeProdutos(), produtos.size()));
        }
        produtos = List.copyOf(produtos);
        arquivosIlegiveis = arquivosIlegiveis.map(List::copyOf);
    }

    // Retorna quantos arquivos não foram lidos; vazio quando a leitura não foi registrada.
    public OptionalInt quantidadeDeArquivosIlegiveis() {
        return arquivosIlegiveis.map(lista -> OptionalInt.of(lista.size())).orElse(OptionalInt.empty());
    }

    // Retorna quantas notas a análise leu, o que decide se abre a tela da nota ou a do lote.
    public int quantidadeDeNotas() {
        return execucao.quantidadeDocumentos();
    }

    // Retorna o produto com o endereço indicado, se ele for desta análise.
    public Optional<ProdutoConferido> produto(String endereco) {
        if (endereco == null || endereco.isBlank()) {
            throw new ConferenciaInvalida("Não há endereço de produto a procurar.");
        }
        return produtos.stream()
                .filter(produto -> produto.endereco().equals(endereco))
                .findFirst();
    }
}
