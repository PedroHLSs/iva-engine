package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import java.nio.file.Path;

// Interface responsável por escrever o papel de trabalho num arquivo; trocar o formato é escrever outra implementação.
public interface ExportadorDePapelDeTrabalho {

    // Escreve o papel de trabalho no destino indicado, substituindo o que houver.
    void exportar(PapelDeTrabalho papel, Path destino);

    // Retorna a extensão de arquivo que esta implementação produz, sem o ponto.
    String extensao();
}
