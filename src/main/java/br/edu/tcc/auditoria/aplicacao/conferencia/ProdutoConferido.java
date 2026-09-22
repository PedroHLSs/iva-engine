package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoItem;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;

import java.util.List;
import java.util.Objects;

/**
 * Um produto de uma nota, com o que foi declarado e o que as regras concluíram.
 *
 * <p>Carrega os apontamentos e as pendências inteiros, e não só a contagem
 * deles: a tela de detalhe monta a sequência de passos que levou ao resultado a
 * partir das evidências que o apontamento já traz, e do motivo que a regra
 * escreveu. Sem eles, a explicação seria texto genérico — que é a única coisa
 * pior que não explicar.</p>
 */
public record ProdutoConferido(
        DadosDoItem dados,
        SituacaoDoProduto situacao,
        List<AchadoRegistrado> achados,
        List<NaoAvaliadaRegistrada> naoAvaliadas) {

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

    /** Identidade estável do produto, e sem identificador: é o resumo do item. */
    public String endereco() {
        return dados.hashLidoNaAnalise().valor();
    }
}
