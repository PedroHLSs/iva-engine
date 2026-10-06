package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa o resultado de uma auditoria, incluindo a execução, documentos auditados, achados e avaliações não concluídas.
// Emenda de 04/10/2026 (D019): carrega quantos documentos repetidos, com o mesmo conteúdo, o lote descartou, para a contagem chegar ao recibo, à planilha e à tela.
public record ResultadoDaAuditoria(
        ExecucaoAuditoria execucao,
        List<DocumentoComItens> documentos,
        List<AchadoLocalizado> achados,
        int quantidadeDeAvaliacoes,
        List<Avaliacao.NaoAvaliada> naoAvaliadas,
        int documentosRepetidosDescartados,
        Optional<ToleranciaDaExecucao> tolerancia) {

    // Emenda de 04/10/2026 (D023): carrega a tolerância de valor da R05 que a auditoria usou, com a origem, para ser gravada junto da execução. Vazia nas aridades anteriores, que não sabem qual foi.

    // Construtor na aridade anterior à D023: a tolerância fica não registrada.
    public ResultadoDaAuditoria(
            ExecucaoAuditoria execucao,
            List<DocumentoComItens> documentos,
            List<AchadoLocalizado> achados,
            int quantidadeDeAvaliacoes,
            List<Avaliacao.NaoAvaliada> naoAvaliadas,
            int documentosRepetidosDescartados) {
        this(execucao, documentos, achados, quantidadeDeAvaliacoes, naoAvaliadas, documentosRepetidosDescartados,
                Optional.empty());
    }

    // Construtor na aridade anterior à D019: nenhum documento repetido descartado.
    public ResultadoDaAuditoria(
            ExecucaoAuditoria execucao,
            List<DocumentoComItens> documentos,
            List<AchadoLocalizado> achados,
            int quantidadeDeAvaliacoes,
            List<Avaliacao.NaoAvaliada> naoAvaliadas) {
        this(execucao, documentos, achados, quantidadeDeAvaliacoes, naoAvaliadas, 0, Optional.empty());
    }

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
        if (tolerancia == null) {
            throw new AuditoriaInvalida("A tolerância vem vazia quando não foi registrada, nunca nula.");
        }
        if (documentosRepetidosDescartados < 0) {
            throw new AuditoriaInvalida("A contagem de documentos repetidos descartados não pode ser negativa.");
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
