package br.edu.tcc.auditoria.aplicacao.conferencia;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Representa as verificações feitas sobre um produto e a situação que resulta delas, pela verificação mais forte.
public record SituacaoDoProduto(List<VerificacaoDoProduto> verificacoes) {

    // Valida que haja ao menos uma verificação, sem regra repetida, e ordena por identificador de regra.
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
        // Ordena por identificador de regra para que a tela saia sempre na mesma sequência.
        verificacoes = verificacoes.stream()
                .sorted(Comparator.comparing(VerificacaoDoProduto::regraId))
                .toList();
    }

    // Retorna a situação do produto: a verificação mais forte, pela ordem declarada em EstadoDeConferencia.
    public EstadoDeConferencia situacao() {
        return verificacoes.stream()
                .map(VerificacaoDoProduto::estado)
                .min(Comparator.comparingInt(EstadoDeConferencia::ordinal))
                .orElseThrow(() -> new ConferenciaInvalida(
                        "Produto sem verificação chegou ao cálculo da situação."));
    }

    // Retorna a contagem dos quatro estados deste produto, inclusive os zeros.
    public ContagemDeEstados contagens() {
        return ContagemDeEstados.de(verificacoes.stream().map(VerificacaoDoProduto::estado).toList());
    }

    // Indica se alguma verificação não concluiu, independente da situação do produto.
    public boolean temVerificacaoNaoConcluida() {
        return contagens().quantidadeDe(EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR) > 0;
    }

    // Retorna por extenso a conta que produziu a situação.
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
