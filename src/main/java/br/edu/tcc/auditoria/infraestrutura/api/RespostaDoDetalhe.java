package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa o detalhe de um produto, a tela mais importante: a situação, o que a nota declarou, o tratamento que a base indica, as descrições lado a lado, a comparação e o passo de cada regra, com a faixa de procedência e o aviso de uso.
public record RespostaDoDetalhe(
        String analiseId,
        DocumentoExposto documento,
        ProdutoExposto produto,
        DeclaracaoExposta declarado,
        DescricaoComparadaExposta descricoes,
        TratamentoExposto tratamento,
        ComparacaoExposta comparacao,
        List<PassoExposto> passos,
        FaixaDeNatureza natureza,
        String aviso) {

    // Valida que o detalhe tenha todos os blocos, um passo para cada verificação, a faixa de procedência e o aviso de uso.
    public RespostaDoDetalhe {
        if (analiseId == null || analiseId.isBlank()) {
            throw new RespostaInvalida("O detalhe precisa dizer de que análise ele é.");
        }
        if (documento == null || produto == null || declarado == null || descricoes == null
                || tratamento == null || comparacao == null) {
            throw new RespostaInvalida(
                    "O detalhe precisa de todos os blocos. Faltando um, a tela mostraria meia resposta "
                            + "com cara de resposta inteira.");
        }
        if (passos == null || passos.isEmpty()) {
            throw new RespostaInvalida(
                    "O detalhe precisa dos passos: produto sem passo é produto que não foi auditado.");
        }
        if (passos.size() != produto.verificacoes().size()) {
            throw new RespostaInvalida(
                    ("O produto tem %d verificação(ões) e o detalhe traz %d passo(s). Passo faltando é "
                            + "regra que rodou e não aparece explicada.")
                            .formatted(produto.verificacoes().size(), passos.size()));
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência. Dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }
        if (aviso == null || aviso.isBlank()) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com o aviso de uso. Ele não é opcional por tela.");
        }
        passos = List.copyOf(passos);
    }
}
