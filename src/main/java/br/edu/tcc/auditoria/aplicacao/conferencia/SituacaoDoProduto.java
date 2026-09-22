package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * As verificações feitas sobre um produto, e a situação que resulta delas.
 *
 * <p>Um produto é avaliado por todas as regras do conjunto, uma avaliação por
 * par (item, regra). Esta é a peça que transforma esse punhado de desfechos na
 * única palavra que cabe numa coluna de tabela — sem que as outras desapareçam
 * no caminho.</p>
 *
 * <h2>A precedência, e o que ela não pode fazer</h2>
 *
 * <p>A situação é a mais forte entre as verificações, pela ordem em que
 * {@link EstadoDeConferencia} declara suas constantes. Uma divergência
 * encontrada prevalece sobre uma verificação que não concluiu, porque a
 * divergência é fato — é a mesma escolha que o R05 já faz entre os três pares
 * que confere.</p>
 *
 * <p>Isso tem um preço, e ele é pago aqui e não escondido: a situação sozinha
 * <strong>não</strong> diz quantas verificações ficaram sem conclusão. Por isso
 * {@link #contagens()} acompanha a situação em todo lugar onde ela aparece, e
 * por isso existe {@link #temVerificacaoNaoConcluida()} — o resumo da nota conta
 * os produtos por ele, não pela situação.</p>
 *
 * <h2>A invariante que importa</h2>
 *
 * <p>{@link EstadoDeConferencia#SEM_DIVERGENCIA_IDENTIFICADA} é o último da
 * ordem, então um produto só chega nele quando <em>todas</em> as suas
 * verificações chegaram. Não há ramo de código que decida isso: é consequência
 * da ordem das constantes, e há teste que sabota a ordem para provar.</p>
 */
public record SituacaoDoProduto(List<VerificacaoDoProduto> verificacoes) {

    public SituacaoDoProduto {
        if (verificacoes == null) {
            throw new ConferenciaInvalida(
                    "Não há verificações de onde tirar a situação do produto.");
        }
        if (verificacoes.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A lista de verificações não pode conter elemento nulo.");
        }
        if (verificacoes.isEmpty()) {
            throw new ConferenciaInvalida(
                    "Um produto sem nenhuma verificação não tem situação, e não pode receber uma por "
                            + "omissão. O motor produz uma avaliação por par (item, regra): chegar aqui "
                            + "com a lista vazia significa que o produto não foi auditado.");
        }
        Set<String> regras = new HashSet<>();
        for (VerificacaoDoProduto verificacao : verificacoes) {
            if (!regras.add(verificacao.regraId())) {
                throw new ConferenciaInvalida(
                        ("A regra %s aparece duas vezes sobre o mesmo produto. O motor produz exatamente "
                                + "uma avaliação por par (item, regra), e contar duas faria a situação e "
                                + "as contagens divergirem do que foi auditado.")
                                .formatted(verificacao.regraId()));
            }
        }
        // Ordem por identificador de regra, para que duas execuções sobre o mesmo
        // produto desenhem a tela na mesma sequência, qualquer que seja a ordem
        // em que quem chama montou a lista.
        verificacoes = verificacoes.stream()
                .sorted(Comparator.comparing(VerificacaoDoProduto::regraId))
                .toList();
    }

    /**
     * A situação do produto: a mais forte entre as verificações.
     *
     * <p>"Mais forte" é a menor posição em {@link EstadoDeConferencia}, e não uma
     * cadeia de condições — trocar a ordem lá muda a precedência aqui, que é
     * justamente a propriedade que o teste de sabotagem exercita.</p>
     */
    public EstadoDeConferencia situacao() {
        return verificacoes.stream()
                .map(VerificacaoDoProduto::estado)
                .min(Comparator.comparingInt(EstadoDeConferencia::ordinal))
                .orElseThrow(() -> new ConferenciaInvalida(
                        "Produto sem verificação chegou ao cálculo da situação."));
    }

    /** Os quatro estados deste produto, sempre os quatro, inclusive os zeros. */
    public ContagemDeEstados contagens() {
        return ContagemDeEstados.de(verificacoes.stream().map(VerificacaoDoProduto::estado).toList());
    }

    /**
     * Se alguma verificação deste produto não concluiu.
     *
     * <p>Independe da situação: um produto com divergência <em>e</em> verificação
     * pendente responde {@code true} aqui e aparece como divergência ali. É a
     * pergunta que impede a precedência de esconder o não concluído na contagem
     * da nota.</p>
     */
    public boolean temVerificacaoNaoConcluida() {
        return contagens().quantidadeDe(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR) > 0;
    }

    /**
     * A conta que produziu a situação, escrita por extenso.
     *
     * <p>Vai junto da situação na resposta e na tela. Número sem procedência numa
     * auditoria é número que ninguém confere, e uma palavra só numa coluna é a
     * versão extrema disso.</p>
     */
    public String comoFoiObtida() {
        StringBuilder detalhe = new StringBuilder();
        ContagemDeEstados contagens = contagens();
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            if (!detalhe.isEmpty()) {
                detalhe.append(", ");
            }
            detalhe.append("%d %s".formatted(contagens.quantidadeDe(estado), estado.rotulo().toLowerCase()));
        }
        return ("entre %d verificação(ões) deste produto — %s —, prevalece a mais forte: %s")
                .formatted(verificacoes.size(), detalhe, situacao().rotulo());
    }
}
