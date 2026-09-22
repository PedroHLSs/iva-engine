package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;

import java.util.List;
import java.util.Objects;

/**
 * Tudo o que a tela mais importante precisa sobre um produto.
 *
 * <p>Quatro blocos, nesta ordem, porque é a ordem em que a pergunta se responde:
 * o que veio no documento, que tratamento a base normativa indica, os dois lado a
 * lado, e por que as regras chegaram ao resultado que chegaram.</p>
 *
 * <p>A identificação do documento vem junto e não é cabeçalho decorativo: o
 * tratamento foi resolvido na data de emissão dele, e sem a data à vista a
 * fundamentação exibida não é conferível.</p>
 */
public record DetalheDoProduto(
        DadosDoDocumento documento,
        ProdutoConferido produto,
        TratamentoIdentificado tratamento,
        ComparacaoDeclaradoEIndicado comparacao,
        List<PassoDaConferencia> passos) {

    public DetalheDoProduto {
        if (documento == null) {
            throw new ConferenciaInvalida("O detalhe precisa dizer de que documento o produto é.");
        }
        if (produto == null) {
            throw new ConferenciaInvalida("Não há produto a detalhar.");
        }
        if (tratamento == null) {
            throw new ConferenciaInvalida(
                    "O detalhe precisa do tratamento identificado, ainda que ele seja o motivo de não "
                            + "ter sido possível determiná-lo.");
        }
        if (comparacao == null) {
            throw new ConferenciaInvalida("O detalhe precisa do quadro de declarado e indicado.");
        }
        if (passos == null || passos.isEmpty()) {
            throw new ConferenciaInvalida(
                    "O detalhe precisa dos passos: produto sem passo é produto que não foi auditado, e "
                            + "a lista de produtos não o traria.");
        }
        if (passos.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("A lista de passos não pode conter elemento nulo.");
        }
        if (passos.size() != produto.situacao().verificacoes().size()) {
            throw new ConferenciaInvalida(
                    ("O produto tem %d verificação(ões) e o detalhe traz %d passo(s). Falta de passo é "
                            + "regra que rodou e não aparece explicada.")
                            .formatted(produto.situacao().verificacoes().size(), passos.size()));
        }
        passos = List.copyOf(passos);
    }

    /** Identidade estável do produto — o resumo do item, sem identificador em texto claro. */
    public String endereco() {
        return produto.endereco();
    }
}
