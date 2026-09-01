package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import java.nio.file.Path;

/**
 * Porta de escrita do papel de trabalho num arquivo.
 *
 * <p>A aplicação monta o conteúdo e não sabe em que formato ele sai. Trocar xlsx
 * por CSV ou por qualquer outra coisa é escrever outra implementação desta
 * interface, sem tocar em o que entra no relatório.</p>
 */
public interface ExportadorDePapelDeTrabalho {

    /** Escreve o papel de trabalho no destino indicado, substituindo o que houver. */
    void exportar(PapelDeTrabalho papel, Path destino);

    /** Extensão de arquivo que esta implementação produz, sem o ponto. */
    String extensao();
}
