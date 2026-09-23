package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia;

import java.util.List;

// Representa as contagens dos quatro estados de uma análise, por produto e por verificação. Vem junto quantos produtos têm alguma verificação sem conclusão, para uma pendência não sumir atrás de uma divergência.
public record ConferenciaExposta(
        int quantidadeDeNotas,
        int quantidadeDeProdutos,
        List<EstadoContado> produtosPorSituacao,
        int produtosComAlgumaVerificacaoNaoConcluida,
        List<EstadoContado> verificacoesPorEstado,
        String comoFoiObtido) {

    // Valida que as duas listas tragam os quatro estados, que haja a explicação de como os números saíram e que as contagens façam sentido.
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

    // Método auxiliar que exige os quatro estados na lista, inclusive os que ficaram em zero.
    private static void exigirOsQuatro(List<EstadoContado> contagem, String nomeDoCampo) {
        if (contagem == null || contagem.size() != EstadoDeConferencia.values().length) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" precisa trazer os %d estados, inclusive os que ficaram em zero. "
                            + "Estado omitido faz quem lê não saber se ele não ocorreu ou se ninguém "
                            + "o contou.").formatted(nomeDoCampo, EstadoDeConferencia.values().length));
        }
    }
}
