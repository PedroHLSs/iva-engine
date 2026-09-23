package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.List;
import java.util.Objects;

// Representa o resultado de uma auditoria, incluindo a execução, documentos auditados, achados e avaliações não concluídas.
public record ResultadoDaAuditoria(
        ExecucaoAuditoria execucao,
        List<DocumentoComItens> documentos,
        List<AchadoLocalizado> achados,
        int quantidadeDeAvaliacoes,
        List<Avaliacao.NaoAvaliada> naoAvaliadas) {

    // Valida os parâmetros do construtor para garantir que o resultado da auditoria seja consistente e não contenha valores nulos ou inconsistentes.
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

    public int quantidadeDeNaoAvaliadas() {
        return naoAvaliadas.size();
    }

    public int quantidadeDeConformes() {
        return quantidadeDeAvaliacoes - naoAvaliadas.size() - achados.size();
    }
}
