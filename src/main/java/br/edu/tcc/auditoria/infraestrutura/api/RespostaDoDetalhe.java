package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * A tela mais importante: um produto, inteiro.
 *
 * <p>Os blocos saem na ordem em que a pergunta se responde. {@code produto} traz a
 * situação com rótulo, explicação e as quatro contagens — as mesmas que a lista
 * mostrou, vindas da mesma montagem, de modo que as duas telas não têm como
 * discordar. {@code declarado} é o que veio no XML. {@code tratamento} é o que a
 * base normativa indica, com IBS e CBS separados. {@code descricoes} põe a
 * descrição da nota ao lado da que o catálogo dá ao NCM, que é o sinal de
 * classificação errada que nenhuma das duas dá sozinha. {@code comparacao} põe
 * declarado e indicado lado a lado, sem emitir veredito. {@code passos} diz por que cada regra chegou
 * ao que chegou.</p>
 *
 * <p>{@code aviso} fecha, e vem do servidor — ver {@link AvisoDeUso}.</p>
 */
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
