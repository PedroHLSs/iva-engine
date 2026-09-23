package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.util.List;
import java.util.Objects;

// Tras os dados de um lote de documentos
public record LoteDeDocumentos(String hashDaEntrada, List<DocumentoComItens> documentos) {

    public LoteDeDocumentos {
        if (hashDaEntrada == null || hashDaEntrada.isBlank()) {
            throw new AuditoriaInvalida(
                    "O lote precisa do resumo da entrada: sem ele a execução não diz o que auditou.");
        }
        if (documentos == null) {
            throw new AuditoriaInvalida(
                    "A lista de documentos deve ser vazia quando a origem não tem nenhum, nunca nula.");
        }
        if (documentos.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de documentos não pode conter elemento nulo.");
        }
        documentos = List.copyOf(documentos);
    }

    public int quantidadeDeItens() {
        return documentos.stream().mapToInt(documento -> documento.itens().size()).sum();
    }

    public boolean vazio() {
        return documentos.isEmpty();
    }
}
