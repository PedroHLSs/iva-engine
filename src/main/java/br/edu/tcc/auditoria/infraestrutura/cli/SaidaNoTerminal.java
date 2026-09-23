package br.edu.tcc.auditoria.infraestrutura.cli;

import org.springframework.stereotype.Component;

import java.io.PrintStream;

// Classe que escreve na saída padrão do processo.
@Component
class SaidaNoTerminal implements Saida {

    private final PrintStream destino;

    // Construtor que escreve no System.out.
    SaidaNoTerminal() {
        this(System.out);
    }

    // Construtor que recebe onde escrever.
    SaidaNoTerminal(PrintStream destino) {
        this.destino = destino;
    }

    // Escreve uma linha no destino.
    @Override
    public void linha(String texto) {
        destino.println(texto);
    }
}
