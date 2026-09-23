package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;

import java.util.List;
import java.util.Objects;

// Representa tudo o que a tela de detalhe precisa sobre um produto: documento, tratamento, comparação e passos.
public record DetalheDoProduto(
        DadosDoDocumento documento,
        ProdutoConferido produto,
        TratamentoIdentificado tratamento,
        ComparacaoDeclaradoEIndicado comparacao,
        List<PassoDaConferencia> passos) {

    // Valida o detalhe e confere que há um passo para cada verificação do produto.
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

    // Retorna o endereço do produto, que é o resumo do item e não traz identificador em texto claro.
    public String endereco() {
        return produto.endereco();
    }
}
