package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa o relatório de acurácia de um conjunto de regras, incluindo informações sobre versões, contagens e métricas por regra.
public record RelatorioDeAcuracia(
        String versaoDoCatalogo,
        String versaoDoConjuntoDeRegras,
        int documentosAuditados,
        int itensAuditados,
        int avaliacoesProduzidas,
        int avaliacoesSemLinhaNoGabarito,
        List<MetricasDaRegra> porRegra,
        List<EnderecoDaAvaliacao> gabaritoSemAvaliacao) {

    public RelatorioDeAcuracia {
        exigirTexto(versaoDoCatalogo, "a versão do catálogo");
        exigirTexto(versaoDoConjuntoDeRegras, "a versão do conjunto de regras");
        exigirNaoNegativo(documentosAuditados, "documentosAuditados");
        exigirNaoNegativo(itensAuditados, "itensAuditados");
        exigirNaoNegativo(avaliacoesProduzidas, "avaliacoesProduzidas");
        exigirNaoNegativo(avaliacoesSemLinhaNoGabarito, "avaliacoesSemLinhaNoGabarito");

        if (porRegra == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O relatório precisa de uma linha por regra do conjunto, ainda que zerada.");
        }
        if (gabaritoSemAvaliacao == null) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A lista de linhas do gabarito sem avaliação deve ser vazia quando todas foram "
                            + "respondidas, nunca nula.");
        }
        if (porRegra.stream().anyMatch(Objects::isNull)
                || gabaritoSemAvaliacao.stream().anyMatch(Objects::isNull)) {
            throw new AvaliacaoDeAcuraciaInvalida("As listas do relatório não podem conter elemento nulo.");
        }

        int semAvaliacaoContado = porRegra.stream()
                .mapToInt(linha -> linha.contagem().semAvaliacao())
                .sum();
        if (semAvaliacaoContado != gabaritoSemAvaliacao.size()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    ("As contagens somam %d linha(s) de gabarito sem avaliação e a lista traz %d. Um "
                            + "relatório internamente contraditório é pior que nenhum.")
                            .formatted(semAvaliacaoContado, gabaritoSemAvaliacao.size()));
        }

        porRegra = List.copyOf(porRegra);
        gabaritoSemAvaliacao = List.copyOf(gabaritoSemAvaliacao);
    }

    public ContagemDeAcuracia consolidado() {
        return porRegra.stream()
                .map(MetricasDaRegra::contagem)
                .reduce(ContagemDeAcuracia.nenhuma(), ContagemDeAcuracia::mais);
    }

    public int linhasDoGabarito() {
        return consolidado().total();
    }

    // Retorna as métricas de acurácia de uma regra específica, se houver.
    public Optional<MetricasDaRegra> daRegra(String regraId) {
        return porRegra.stream()
                .filter(linha -> linha.regraId().equals(regraId))
                .findFirst();
    }
    private static void exigirTexto(String valor, String oQueFalta) {
        if (valor == null || valor.isBlank()) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "O relatório de acurácia precisa de %s: sem isso o resultado não é reproduzível."
                            .formatted(oQueFalta));
        }
    }

    private static void exigirNaoNegativo(int valor, String nomeDoCampo) {
        if (valor < 0) {
            throw new AvaliacaoDeAcuraciaInvalida(
                    "A contagem \"%s\" não pode ser negativa, mas veio %d.".formatted(nomeDoCampo, valor));
        }
    }
}
