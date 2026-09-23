package br.edu.tcc.auditoria.infraestrutura.cli;

// Interface que todo comando da linha de comando implementa.
interface Comando {

    // Retorna o nome pelo qual o comando é chamado.
    String nome();

    // Retorna uma linha explicando o que o comando faz.
    String descricao();

    // Retorna o modo de usar, com as opções aceitas.
    String modoDeUsar();

    // Executa o comando.
    void executar(Argumentos argumentos);
}
