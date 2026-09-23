package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.SituacaoDaNatureza;

import java.util.ArrayList;
import java.util.List;

// Representa a faixa que diz de onde veio o catálogo usado: dado de demonstração ou normativo. Vai em toda resposta de resultado, e no caso misto lista quais tabelas são de demonstração.
public record FaixaDeNatureza(
        String situacao,
        String rotulo,
        String explicacao,
        boolean exigeAviso,
        String versaoDoCatalogo,
        List<TabelaExposta> porTabela,
        List<String> tabelasFicticias) {

    // Valida que a faixa tenha código, rótulo, explicação e versão do catálogo, e que as listas não venham nulas.
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

    // Método estático que monta a faixa a partir da procedência da carga.
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

    // Representa uma tabela da carga e a procedência que ela declarou.
    public record TabelaExposta(String tabela, String natureza, String rotulo) {

        // Valida que a tabela tenha nome, procedência e rótulo.
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
