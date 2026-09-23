package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Objects;

// Representa o papel de trabalho de uma execução, pronto para virar planilha, sempre com a identificação da execução.
public record PapelDeTrabalho(
        ExecucaoAuditoria execucao,
        List<LinhaDeAchado> achados,
        List<LinhaNaoAvaliada> naoAvaliados,
        List<MotivoAgrupado> motivosAgrupados,
        int itensNaoAvaliados) {

    // Valida o papel de trabalho e confere que os motivos agrupados e os achados batem com o detalhe e com o recibo da execução.
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

    // Indica se a rodada não produziu apontamento nenhum.
    public boolean semAchados() {
        return achados.isEmpty();
    }

    public int quantidadeDeNaoAvaliados() {
        return naoAvaliados.size();
    }

    // Método auxiliar que copia a lista, recusando lista nula ou com elemento nulo.
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
