package br.edu.tcc.auditoria.aplicacao.acuracia;

import java.nio.file.Path;

/**
 * Porta de escrita do relatório de acurácia num arquivo.
 *
 * <p>A aplicação monta as métricas e não sabe em que formato elas saem. Separada
 * da porta do papel de trabalho de propósito: o papel de trabalho é o produto
 * que uma pessoa confere item a item, e este é o resultado empírico do trabalho,
 * feito para entrar numa tabela.</p>
 */
public interface EscritorDeRelatorioDeAcuracia {

    /** Escreve o relatório no destino indicado, substituindo o que houver. */
    void escrever(RelatorioDeAcuracia relatorio, Path destino);

    /** Extensão de arquivo que esta implementação produz, sem o ponto. */
    String extensao();
}
