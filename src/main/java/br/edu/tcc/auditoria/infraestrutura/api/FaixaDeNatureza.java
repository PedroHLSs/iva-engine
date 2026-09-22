package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.SituacaoDaNatureza;

import java.util.ArrayList;
import java.util.List;

/**
 * A faixa que diz de onde veio o que a tela está mostrando.
 *
 * <h2>Vai em toda resposta de resultado, e não só onde o catálogo aparece</h2>
 *
 * <p>A situação de um produto foi produzida contra uma carga. Se a carga era de
 * demonstração, a situação é de demonstração — mesmo que a tela de lista não
 * mostre uma linha sequer do catálogo. Pôr a faixa só na tela que exibe a tabela
 * deixaria de fora justamente as telas que as pessoas mais olham.</p>
 *
 * <h2>Lista quais tabelas, e não só que há mistura</h2>
 *
 * <p>No caso parcialmente fictício, {@code tabelasFicticias} nomeia as tabelas de
 * demonstração. É o que permite a quem lê saber em que parte da tela pode
 * confiar — um aviso genérico de "há dado fictício aqui" desqualifica a tela
 * inteira e não ajuda a usar a parte boa.</p>
 */
public record FaixaDeNatureza(
        String situacao,
        String rotulo,
        String explicacao,
        boolean exigeAviso,
        String versaoDoCatalogo,
        List<TabelaExposta> porTabela,
        List<String> tabelasFicticias) {

    public FaixaDeNatureza {
        if (situacao == null || situacao.isBlank()
                || rotulo == null || rotulo.isBlank()
                || explicacao == null || explicacao.isBlank()) {
            throw new RespostaInvalida(
                    "A faixa precisa do código, do rótulo e da explicação: código sozinho faz quem "
                            + "consome escrever o aviso por conta própria.");
        }
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new RespostaInvalida("A faixa precisa dizer de qual carga ela fala.");
        }
        if (porTabela == null || tabelasFicticias == null) {
            throw new RespostaInvalida(
                    "As listas devem ser vazias quando não há nada a listar, nunca nulas.");
        }
        porTabela = List.copyOf(porTabela);
        tabelasFicticias = List.copyOf(tabelasFicticias);
    }

    static FaixaDeNatureza de(NaturezaDaCarga natureza, String versaoDoCatalogo) {
        SituacaoDaNatureza situacao = natureza.situacao();

        List<TabelaExposta> porTabela = new ArrayList<>();
        natureza.declaradas().forEach((tabela, declarada) ->
                porTabela.add(new TabelaExposta(tabela, declarada.name(), declarada.rotulo())));

        return new FaixaDeNatureza(
                situacao.name(),
                situacao.rotulo(),
                situacao.explicacao(),
                situacao.exigeAviso(),
                versaoDoCatalogo,
                porTabela,
                natureza.tabelasFicticias());
    }

    /** Uma tabela da carga e a procedência declarada dela. */
    public record TabelaExposta(String tabela, String natureza, String rotulo) {

        public TabelaExposta {
            if (tabela == null || tabela.isBlank()) {
                throw new RespostaInvalida("A linha da faixa precisa dizer de que tabela ela é.");
            }
            if (natureza == null || natureza.isBlank() || rotulo == null || rotulo.isBlank()) {
                throw new RespostaInvalida(
                        "A tabela %s precisa da natureza declarada e do rótulo dela.".formatted(tabela));
            }
            if (Natureza.valueOf(natureza).rotulo().isBlank()) {
                throw new RespostaInvalida("Natureza sem rótulo não chega à tela.");
            }
        }
    }
}
