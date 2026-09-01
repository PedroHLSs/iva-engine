package br.edu.tcc.auditoria.aplicacao.auditoria;

import java.nio.file.Path;

/**
 * Porta de leitura de um lote de documentos fiscais.
 *
 * <p>A aplicação pede o lote de uma origem e recebe documentos de domínio já
 * normalizados e pseudonimizados. Leiaute de XML, ZIP, encoding e arquivo
 * corrompido são problema de quem implementa esta interface, e não vazam para
 * cá: nenhum caso de uso deste sistema conhece o esquema da NF-e (D005).</p>
 */
public interface FonteDeLoteDeDocumentos {

    /**
     * Lê todos os documentos da origem indicada — um diretório ou um arquivo ZIP.
     *
     * <p>Arquivo ilegível é registrado como falha por quem implementa e omitido
     * do resultado, sem interromper a leitura dos demais.</p>
     */
    LoteDeDocumentos abrir(Path origem);
}
