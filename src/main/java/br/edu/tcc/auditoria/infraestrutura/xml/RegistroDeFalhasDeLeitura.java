package br.edu.tcc.auditoria.infraestrutura.xml;

// Interface que recebe as falhas encontradas na leitura de um lote. O LeitorLote devolve só os documentos, e as falhas precisam de outra saída, que pode ser a tela, o relatório ou uma lista no teste.
@FunctionalInterface
public interface RegistroDeFalhasDeLeitura {

    // Recebe uma falha de leitura.
    void registrar(FalhaDeLeitura falha);
}
