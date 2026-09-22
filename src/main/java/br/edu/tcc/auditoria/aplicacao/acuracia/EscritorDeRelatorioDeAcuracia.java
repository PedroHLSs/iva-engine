package br.edu.tcc.auditoria.aplicacao.acuracia;

import java.nio.file.Path;

// Define para escrever relatórios de acurácia em arquivos. 
public interface EscritorDeRelatorioDeAcuracia {

    void escrever(RelatorioDeAcuracia relatorio, Path destino);

    String extensao();
}
