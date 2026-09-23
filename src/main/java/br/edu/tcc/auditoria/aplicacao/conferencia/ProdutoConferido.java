package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoItem;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;

import java.util.List;
import java.util.Objects;

// Representa um produto de uma nota, com o item declarado, a situação e os apontamentos e pendências inteiros.
public record ProdutoConferido(
        DadosDoItem dados,
        SituacaoDoProduto situacao,
        List<AchadoRegistrado> achados,
        List<NaoAvaliadaRegistrada> naoAvaliadas) {

    // Valida que o produto tenha item, situação e listas não nulas.
    public ProdutoConferido {
        if (dados == null) {
            throw new ConferenciaInvalida("O produto conferido precisa do item declarado.");
        }
        if (situacao == null) {
            throw new ConferenciaInvalida(
                    "O produto conferido precisa da situação: é o que a tela mostra na coluna.");
        }
        if (achados == null || naoAvaliadas == null) {
            throw new ConferenciaInvalida(
                    "As listas de apontamento e de pendência devem ser vazias quando não há nenhum, "
                            + "nunca nulas.");
        }
        if (achados.stream().anyMatch(Objects::isNull)
                || naoAvaliadas.stream().anyMatch(Objects::isNull)) {
            throw new ConferenciaInvalida("Nenhuma das duas listas pode conter elemento nulo.");
        }
        achados = List.copyOf(achados);
        naoAvaliadas = List.copyOf(naoAvaliadas);
    }

    public int numeroItem() {
        return dados.numeroItem();
    }

    // Retorna o endereço do produto, que é o resumo do item e não traz identificador em texto claro.
    public String endereco() {
        return dados.hashLidoNaAnalise().valor();
    }
}
