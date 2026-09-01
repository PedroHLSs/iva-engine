package br.edu.tcc.auditoria.infraestrutura.cli;

import org.springframework.stereotype.Component;

import java.io.PrintStream;

/** Saída padrão do processo. */
@Component
class SaidaNoTerminal implements Saida {

    private final PrintStream destino;

    SaidaNoTerminal() {
        this(System.out);
    }

    SaidaNoTerminal(PrintStream destino) {
        this.destino = destino;
    }

    @Override
    public void linha(String texto) {
        destino.println(texto);
    }
}
