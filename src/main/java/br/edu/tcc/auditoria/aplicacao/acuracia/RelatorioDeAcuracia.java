package br.edu.tcc.auditoria.aplicacao.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.ContagemDeAcuracia;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O que a medição de acurácia produziu: o resultado empírico do trabalho.
 *
 * <h2>Consolidado é soma de células, não média de métricas</h2>
 *
 * <p>{@link #consolidado()} soma as contagens de todas as regras e calcula
 * precisão, recall e F1 sobre essa soma. Não é a média das precisões por regra.
 * A diferença importa: a média trataria uma regra com três linhas rotuladas
 * igual a uma com duzentas, e obrigaria a decidir o que fazer com as regras de
 * métrica indefinida — decisão que não tem resposta defensável. Somando
 * células, uma regra sem linha no gabarito contribui com zero em tudo e não
 * distorce nada.</p>
 *
 * <h2>A identificação da rodada abre o relatório</h2>
 *
 * <p>Pelo mesmo motivo do papel de trabalho (D007): um número de acurácia que
 * não diz contra qual catálogo e qual versão de regras foi obtido não é
 * reproduzível, e um resultado não reproduzível não sustenta afirmação nenhuma
 * num trabalho acadêmico.</p>
 *
 * @param versaoDoCatalogo             carga de catálogo usada na medição
 * @param versaoDoConjuntoDeRegras     conjunto de regras usado na medição
 * @param documentosAuditados          documentos lidos da origem
 * @param itensAuditados               itens somados de todos os documentos
 * @param avaliacoesProduzidas         avaliações que o motor gerou, rotuladas ou não
 * @param avaliacoesSemLinhaNoGabarito avaliações do motor que o gabarito não rotula
 * @param porRegra                     uma linha por regra do conjunto, na ordem de aplicação
 * @param gabaritoSemAvaliacao         linhas do gabarito que o motor não respondeu
 */
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

    /** A soma das contagens de todas as regras. */
    public ContagemDeAcuracia consolidado() {
        return porRegra.stream()
                .map(MetricasDaRegra::contagem)
                .reduce(ContagemDeAcuracia.nenhuma(), ContagemDeAcuracia::mais);
    }

    /** Quantas linhas o gabarito trouxe, medidas ou não. */
    public int linhasDoGabarito() {
        return consolidado().total();
    }

    /** Métricas da regra indicada, se ela está no relatório. */
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
