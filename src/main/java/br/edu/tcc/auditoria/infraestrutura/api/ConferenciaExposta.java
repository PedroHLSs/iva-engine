package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.util.List;

/**
 * Os quatro estados de uma análise, nos dois níveis em que eles existem.
 *
 * <h2>Por que dois níveis, e por que o terceiro número</h2>
 *
 * <p>{@code produtosPorSituacao} conta produtos pela verificação mais forte de
 * cada um. É o número que a tela mostra, e ele tem um efeito colateral
 * aritmético: um produto com uma divergência e três pendências é contado como
 * divergência, e some da contagem de não concluídos.</p>
 *
 * <p>Por isso vêm junto {@code produtosComAlgumaVerificacaoNaoConcluida}, que
 * conta produto a produto e não pela situação, e
 * {@code verificacoesPorEstado}, que é o mesmo total sem precedência nenhuma
 * aplicada. Os três juntos não deixam pendência sumir.</p>
 *
 * <p>Arquivo ilegível não está aqui, e é de propósito: ele não é um dos quatro
 * estados. Fica no bloco de leitura, com nome próprio.</p>
 */
public record ConferenciaExposta(
        int quantidadeDeNotas,
        int quantidadeDeProdutos,
        List<EstadoContado> produtosPorSituacao,
        int produtosComAlgumaVerificacaoNaoConcluida,
        List<EstadoContado> verificacoesPorEstado,
        String comoFoiObtido) {

    public ConferenciaExposta {
        exigirOsQuatro(produtosPorSituacao, "produtosPorSituacao");
        exigirOsQuatro(verificacoesPorEstado, "verificacoesPorEstado");
        if (comoFoiObtido == null || comoFoiObtido.isBlank()) {
            throw new RespostaInvalida(
                    "A conferência precisa dizer como os números foram obtidos: eles não estão "
                            + "gravados em coluna nenhuma, são calculados.");
        }
        if (quantidadeDeNotas < 0 || quantidadeDeProdutos < 0
                || produtosComAlgumaVerificacaoNaoConcluida < 0) {
            throw new RespostaInvalida("Contagem da conferência não pode ser negativa.");
        }
        if (produtosComAlgumaVerificacaoNaoConcluida > quantidadeDeProdutos) {
            throw new RespostaInvalida(
                    ("Há %d produto(s) com verificação não concluída e só %d produto(s) no total.")
                            .formatted(produtosComAlgumaVerificacaoNaoConcluida, quantidadeDeProdutos));
        }
        produtosPorSituacao = List.copyOf(produtosPorSituacao);
        verificacoesPorEstado = List.copyOf(verificacoesPorEstado);
    }

    private static void exigirOsQuatro(List<EstadoContado> contagem, String nomeDoCampo) {
        if (contagem == null || contagem.size() != EstadoDeConferencia.values().length) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" precisa trazer os %d estados, inclusive os que ficaram em zero. "
                            + "Estado omitido faz quem lê não saber se ele não ocorreu ou se ninguém "
                            + "o contou.").formatted(nomeDoCampo, EstadoDeConferencia.values().length));
        }
    }
}
