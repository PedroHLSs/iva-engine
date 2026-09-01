package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Objects;

/**
 * O papel de trabalho de uma execução, pronto para virar planilha.
 *
 * <p>É um modelo de saída: não sabe o que é xlsx, não conhece POI e não decide
 * nada. Quem monta busca os dados; quem exporta desenha as células. A separação
 * existe porque trocar o formato do arquivo — CSV, ODS, PDF — não pode mexer em
 * o que entra no relatório.</p>
 *
 * <h2>A identificação da execução vem junto, sempre</h2>
 *
 * <p>{@link ExecucaoAuditoria} traz data, versão de catálogo, versão do conjunto
 * de regras e resumo da entrada. Sem esse bloco, uma planilha encontrada numa
 * pasta meses depois não responde contra o quê foi produzida, e um apontamento
 * que hoje procede pode ter deixado de proceder por mudança de tabela — sem que
 * a planilha diga qual tabela era. Não é cabeçalho decorativo: é o que a torna
 * conferível.</p>
 *
 * @param naoAvaliados      uma linha por avaliação que não concluiu
 * @param motivosAgrupados  os mesmos motivos, agrupados por regra e por texto
 * @param itensNaoAvaliados quantidade de itens distintos atingidos por ao menos
 *                          uma avaliação não concluída
 */
public record PapelDeTrabalho(
        ExecucaoAuditoria execucao,
        List<LinhaDeAchado> achados,
        List<LinhaNaoAvaliada> naoAvaliados,
        List<MotivoAgrupado> motivosAgrupados,
        int itensNaoAvaliados) {

    public PapelDeTrabalho {
        if (execucao == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O papel de trabalho precisa da identificação da execução. Sem ela a planilha não "
                            + "diz contra qual catálogo nem com que regras foi produzida, e deixa de ser "
                            + "conferível depois.");
        }
        achados = copiar(achados, "achados");
        naoAvaliados = copiar(naoAvaliados, "não avaliados");
        motivosAgrupados = copiar(motivosAgrupados, "motivos agrupados");

        if (itensNaoAvaliados < 0) {
            throw new PapelDeTrabalhoInvalido("A contagem de itens não avaliados não pode ser negativa.");
        }
        int somaDosAgrupados = motivosAgrupados.stream()
                .mapToInt(MotivoAgrupado::quantidade)
                .sum();
        if (somaDosAgrupados != naoAvaliados.size()) {
            throw new PapelDeTrabalhoInvalido(
                    ("Os motivos agrupados somam %d e há %d avaliações não concluídas. O resumo não pode "
                            + "divergir do detalhe: é a primeira coisa que quem confere confere.")
                            .formatted(somaDosAgrupados, naoAvaliados.size()));
        }
        if (execucao.quantidadeDeAchados() != achados.size()) {
            throw new PapelDeTrabalhoInvalido(
                    ("O recibo da execução conta %d apontamentos e a planilha traria %d linhas. Isso "
                            + "significa que os apontamentos carregados não são os daquela execução.")
                            .formatted(execucao.quantidadeDeAchados(), achados.size()));
        }
    }

    /** Indica se a rodada não produziu apontamento nenhum. */
    public boolean semAchados() {
        return achados.isEmpty();
    }

    /** Total de avaliações que não concluíram, contando todas as regras. */
    public int quantidadeDeNaoAvaliados() {
        return naoAvaliados.size();
    }

    private static <T> List<T> copiar(List<T> linhas, String oQue) {
        if (linhas == null) {
            throw new PapelDeTrabalhoInvalido(
                    "A lista de %s deve ser vazia quando não há nenhum, nunca nula.".formatted(oQue));
        }
        if (linhas.stream().anyMatch(Objects::isNull)) {
            throw new PapelDeTrabalhoInvalido(
                    "A lista de %s não pode conter elemento nulo.".formatted(oQue));
        }
        return List.copyOf(linhas);
    }
}
