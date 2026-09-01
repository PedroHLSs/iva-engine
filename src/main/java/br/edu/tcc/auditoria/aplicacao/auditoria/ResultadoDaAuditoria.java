package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.List;
import java.util.Objects;

/**
 * O que uma rodada de auditoria produziu.
 *
 * <p>Traz o recibo da execução, os documentos auditados, os apontamentos com o
 * resumo do item de cada um, e as avaliações que não concluíram.</p>
 *
 * <h2>Por que as não concluídas vêm inteiras, e não contadas</h2>
 *
 * <p>Uma avaliação que não concluiu não é conformidade e não é apontamento: é a
 * declaração de que faltou dado no documento ou faltou tabela no catálogo. Ela
 * não gera linha de apontamento — não há o que apontar — mas o motivo de cada
 * uma precisa chegar ao papel de trabalho. Um lote em que nada pôde ser avaliado
 * se pareceria com um lote sem incoerências, e o relatório tem de deixar claro
 * qual dos dois é.</p>
 *
 * <p>Até a Etapa 5 aqui havia só a contagem. A Etapa 6 passou a exigir o motivo
 * de cada uma, e a contagem virou {@link #quantidadeDeNaoAvaliadas()}, derivada
 * da lista — ver D007.</p>
 *
 * @param quantidadeDeAvaliacoes total de avaliações feitas (regras x itens)
 * @param naoAvaliadas           avaliações que não concluíram, cada uma com seu motivo
 */
public record ResultadoDaAuditoria(
        ExecucaoAuditoria execucao,
        List<DocumentoComItens> documentos,
        List<AchadoLocalizado> achados,
        int quantidadeDeAvaliacoes,
        List<Avaliacao.NaoAvaliada> naoAvaliadas) {

    public ResultadoDaAuditoria {
        if (execucao == null) {
            throw new AuditoriaInvalida("O resultado da auditoria precisa do recibo da execução.");
        }
        if (documentos == null) {
            throw new AuditoriaInvalida(
                    "A lista de documentos auditados deve ser vazia quando não houve nenhum, nunca nula.");
        }
        if (achados == null) {
            throw new AuditoriaInvalida(
                    "A lista de apontamentos deve ser vazia quando não houve nenhum, nunca nula.");
        }
        if (naoAvaliadas == null) {
            throw new AuditoriaInvalida(
                    "A lista de avaliações não concluídas deve ser vazia quando todas concluíram, "
                            + "nunca nula.");
        }
        if (documentos.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de documentos auditados não pode conter elemento nulo.");
        }
        if (achados.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de apontamentos não pode conter elemento nulo.");
        }
        if (naoAvaliadas.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida(
                    "A lista de avaliações não concluídas não pode conter elemento nulo.");
        }
        if (quantidadeDeAvaliacoes < 0) {
            throw new AuditoriaInvalida("Contagem de avaliações não pode ser negativa.");
        }
        if (naoAvaliadas.size() > quantidadeDeAvaliacoes) {
            throw new AuditoriaInvalida(
                    "Há mais avaliações não concluídas (%d) que avaliações feitas (%d)."
                            .formatted(naoAvaliadas.size(), quantidadeDeAvaliacoes));
        }
        if (execucao.quantidadeDeAchados() != achados.size()) {
            throw new AuditoriaInvalida(
                    ("O recibo da execução conta %d apontamentos e a lista traz %d. A contagem do "
                            + "relatório não pode divergir do que foi apontado.")
                            .formatted(execucao.quantidadeDeAchados(), achados.size()));
        }
        documentos = List.copyOf(documentos);
        achados = List.copyOf(achados);
        naoAvaliadas = List.copyOf(naoAvaliadas);
    }

    /** Quantidade de avaliações que não concluíram. */
    public int quantidadeDeNaoAvaliadas() {
        return naoAvaliadas.size();
    }

    /** Avaliações que concluíram sem encontrar incoerência. */
    public int quantidadeDeConformes() {
        return quantidadeDeAvaliacoes - naoAvaliadas.size() - achados.size();
    }
}
