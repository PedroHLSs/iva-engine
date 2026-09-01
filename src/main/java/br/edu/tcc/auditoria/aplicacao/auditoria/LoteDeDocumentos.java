package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.util.List;
import java.util.Objects;

/**
 * Os documentos lidos de uma origem, com o resumo do que entrou.
 *
 * <p>{@code hashDaEntrada} identifica o conjunto de arquivos processados: mesmo
 * conjunto, mesmo resumo. É o que permite a uma execução de auditoria dizer
 * depois sobre qual entrada ela foi produzida, e reconhecer quando o mesmo lote
 * rodou duas vezes.</p>
 *
 * <p>Arquivos ilegíveis não aparecem aqui. Eles são registrados como falha por
 * quem leu a origem e não interrompem o lote — um XML corrompido no meio de mil
 * não pode impedir a auditoria dos outros novecentos e noventa e nove.</p>
 */
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

    /** Quantidade de itens somando todos os documentos do lote. */
    public int quantidadeDeItens() {
        return documentos.stream().mapToInt(documento -> documento.itens().size()).sum();
    }

    /** Indica se a origem não produziu nenhum documento legível. */
    public boolean vazio() {
        return documentos.isEmpty();
    }
}
