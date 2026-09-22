package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Os quatro números de uma nota, ou de um lote inteiro — mais os que a
 * precedência tornaria invisíveis.
 *
 * <h2>Por que não bastam quatro números</h2>
 *
 * <p>{@link SituacaoDoProduto} resolve cada produto numa situação só, e a
 * situação mais forte vence. Consequência aritmética: um produto com uma
 * divergência e três verificações que não concluíram é contado em "possível
 * divergência", e some da contagem de não concluídos. Se o resumo trouxesse só
 * as quatro contagens de produtos, o lote pareceria ter menos pendência do que
 * tem.</p>
 *
 * <p>Daí os dois campos que acompanham: {@link #produtosComAlgumaNaoConcluida()}
 * conta produtos com ao menos uma verificação pendente <em>qualquer que seja a
 * situação deles</em>, e {@link #verificacoesPorEstado()} traz os quatro no
 * nível da avaliação, onde nenhuma precedência foi aplicada.</p>
 *
 * <h2>Arquivo ilegível não mora aqui, e é de propósito</h2>
 *
 * <p>Um arquivo que não pôde ser lido não é nota sem divergência: é ausência, e
 * ausência não é um dos quatro estados. Ele não aparece neste tipo porque não
 * há nenhum campo deste tipo a que ele pudesse ser somado por descuido — a
 * contagem de ilegíveis viaja ao lado, na resposta da análise, com nome
 * próprio.</p>
 */
public record ResumoDaConferencia(
        ContagemDeEstados produtosPorSituacao,
        int produtosComAlgumaNaoConcluida,
        ContagemDeEstados verificacoesPorEstado,
        String comoFoiObtido) {

    public ResumoDaConferencia {
        if (produtosPorSituacao == null || verificacoesPorEstado == null) {
            throw new ConferenciaInvalida(
                    "O resumo precisa das duas contagens: a de produtos e a de verificações. Uma só "
                            + "deixaria quem lê escolher qual delas acha que está vendo.");
        }
        if (comoFoiObtido == null || comoFoiObtido.isBlank()) {
            throw new ConferenciaInvalida(
                    "O resumo precisa dizer como foi obtido: estes números não estão gravados em coluna "
                            + "nenhuma, são calculados aqui.");
        }
        if (produtosComAlgumaNaoConcluida < 0) {
            throw new ConferenciaInvalida(
                    "A contagem de produtos com verificação não concluída não pode ser negativa.");
        }

        int produtos = produtosPorSituacao.total();
        if (produtosComAlgumaNaoConcluida > produtos) {
            throw new ConferenciaInvalida(
                    ("Há %d produto(s) com verificação não concluída e só %d produto(s) no total.")
                            .formatted(produtosComAlgumaNaoConcluida, produtos));
        }

        int comSituacaoNaoConcluida =
                produtosPorSituacao.quantidadeDe(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR);
        if (produtosComAlgumaNaoConcluida < comSituacaoNaoConcluida) {
            throw new ConferenciaInvalida(
                    ("%d produto(s) têm situação \"%s\", mas o resumo diz que só %d têm alguma "
                            + "verificação não concluída. Todo produto naquela situação tem ao menos "
                            + "uma.").formatted(
                            comSituacaoNaoConcluida,
                            EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR.rotulo(),
                            produtosComAlgumaNaoConcluida));
        }

        int semDivergencia =
                produtosPorSituacao.quantidadeDe(EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA);
        if (produtosComAlgumaNaoConcluida > produtos - semDivergencia) {
            throw new ConferenciaInvalida(
                    ("O resumo diz que %d produto(s) têm verificação não concluída, mas %d dos %d "
                            + "produtos estão em \"%s\" — e esses, por construção, não têm nenhuma. "
                            + "Somar as duas coisas é exatamente o que esta camada existe para "
                            + "impedir.").formatted(
                            produtosComAlgumaNaoConcluida,
                            semDivergencia,
                            produtos,
                            EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA.rotulo()));
        }

        if (verificacoesPorEstado.total() < produtos) {
            throw new ConferenciaInvalida(
                    ("Há %d produto(s) e só %d verificação(ões). Todo produto auditado tem ao menos "
                            + "uma.").formatted(produtos, verificacoesPorEstado.total()));
        }
    }

    /** O resumo das situações dadas, com os quatro números em cada nível. */
    public static ResumoDaConferencia de(Collection<SituacaoDoProduto> produtos) {
        if (produtos == null) {
            throw new ConferenciaInvalida(
                    "A coleção de produtos deve ser vazia quando a análise não leu nenhum, nunca nula.");
        }
        if (produtos.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A coleção de produtos não pode conter elemento nulo.");
        }

        List<EstadoDeConferencia> situacoes = new ArrayList<>();
        List<EstadoDeConferencia> estadosDasVerificacoes = new ArrayList<>();
        int comAlgumaNaoConcluida = 0;
        for (SituacaoDoProduto produto : produtos) {
            situacoes.add(produto.situacao());
            produto.verificacoes().forEach(
                    verificacao -> estadosDasVerificacoes.add(verificacao.estado()));
            if (produto.temVerificacaoNaoConcluida()) {
                comAlgumaNaoConcluida++;
            }
        }

        ContagemDeEstados porSituacao = ContagemDeEstados.de(situacoes);
        ContagemDeEstados porVerificacao = ContagemDeEstados.de(estadosDasVerificacoes);
        return new ResumoDaConferencia(
                porSituacao,
                comAlgumaNaoConcluida,
                porVerificacao,
                derivacao(porSituacao, comAlgumaNaoConcluida, porVerificacao));
    }

    /** Quantos produtos a análise resumiu. */
    public int quantidadeDeProdutos() {
        return produtosPorSituacao.total();
    }

    private static String derivacao(
            ContagemDeEstados porSituacao, int comAlgumaNaoConcluida, ContagemDeEstados porVerificacao) {

        return ("%d produto(s) classificados pela verificação mais forte de cada um, sobre %d "
                + "verificação(ões) ao todo. %d produto(s) têm ao menos uma verificação não "
                + "concluída — contagem feita produto a produto, e não pela situação, porque a "
                + "situação mais forte esconderia parte deles.").formatted(
                porSituacao.total(), porVerificacao.total(), comAlgumaNaoConcluida);
    }
}
