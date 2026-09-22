package br.edu.tcc.auditoria.aplicacao.analise;

// Tem como funcao criar novas instancias de LeituraDeLote, que sao usadas para ler arquivos de analise.
@FunctionalInterface
public interface FabricaDeLeituraDeLote {

    LeituraDeLote nova();
}
