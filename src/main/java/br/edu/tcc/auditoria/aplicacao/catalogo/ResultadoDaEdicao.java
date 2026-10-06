package br.edu.tcc.auditoria.aplicacao.catalogo;

import java.util.List;

// Representa o que a edição fez: qual efeito teve, de qual carga partiu, qual carga resultou e quais tabelas foram substituídas.
public record ResultadoDaEdicao(
        EfeitoDaEdicao efeito,
        String versaoDeOrigem,
        String versaoResultante,
        List<String> tabelasSubstituidas) {

    // Guarda uma cópia imutável da lista.
    public ResultadoDaEdicao {
        tabelasSubstituidas = List.copyOf(tabelasSubstituidas);
    }
}
