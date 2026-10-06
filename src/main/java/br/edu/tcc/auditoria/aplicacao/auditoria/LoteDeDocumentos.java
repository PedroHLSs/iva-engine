package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Tras os dados de um lote de documentos
// Emenda de 04/10/2026 (D019): um lote não tem dois documentos com a mesma chave de acesso — o banco guarda um por chave, e um recibo que contasse dois afirmaria o que não ficou gravado. Quem lê o lote resolve as cópias antes: idênticas viram uma, e documentosRepetidosDescartados diz quantas foram descartadas; divergentes não entram.
public record LoteDeDocumentos(String hashDaEntrada, List<DocumentoComItens> documentos, int documentosRepetidosDescartados) {

    // Construtor na aridade anterior à D019: sem cópia descartada, o que a recusa de chave repetida garante.
    public LoteDeDocumentos(String hashDaEntrada, List<DocumentoComItens> documentos) {
        this(hashDaEntrada, documentos, 0);
    }

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
        if (documentosRepetidosDescartados < 0) {
            throw new AuditoriaInvalida("A contagem de documentos repetidos descartados não pode ser negativa.");
        }
        Set<String> chaves = new HashSet<>();
        for (DocumentoComItens documento : documentos) {
            if (!chaves.add(documento.documento().chaveAcesso().valor())) {
                // A chave não entra na mensagem: ela carrega o CNPJ do emitente.
                throw new AuditoriaInvalida(
                        "O lote traz dois documentos com a mesma chave de acesso. O banco guarda um por "
                                + "chave, e auditar os dois faria o recibo contar o que não ficou gravado; "
                                + "quem lê o lote precisa descartar a cópia idêntica, ou deixar as duas de "
                                + "fora quando o conteúdo diverge.");
            }
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
