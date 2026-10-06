package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;

// Representa um documento lido junto com o arquivo de onde veio (D019). A origem é o caminho como o LeitorLote o escreve, com a pasta; quem grava passa por OrigemDeArquivoIlegivel antes.
public record DocumentoLido(String origem, DocumentoComItens documento) {

    // Valida que haja origem e documento.
    public DocumentoLido {
        if (origem == null || origem.isBlank()) {
            throw new IllegalArgumentException("O documento lido precisa dizer de qual arquivo veio.");
        }
        if (documento == null) {
            throw new IllegalArgumentException("Não há documento lido sem documento.");
        }
    }
}
