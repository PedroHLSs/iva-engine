package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.math.BigDecimal;

// Guarda a diferença máxima que a regra de valor aceita sem apontar. Quem define é quem usa o sistema, não a lei; zero quer dizer que o valor tem de bater exato.
public record ToleranciaDeValor(BigDecimal quantia) {

    // Não aceita tolerância vazia nem negativa.
    public ToleranciaDeValor {
        if (quantia == null) {
            throw new RegraInvalida("A tolerância precisa de uma quantia. Para exigir igualdade exata, use zero.");
        }
        if (quantia.signum() < 0) {
            throw new RegraInvalida(
                    "A tolerância não pode ser negativa, mas veio %s.".formatted(quantia.toPlainString()));
        }
    }

    // Cria a tolerância com o valor informado.
    public static ToleranciaDeValor de(BigDecimal quantia) {
        return new ToleranciaDeValor(quantia);
    }

    // Cria a tolerância zero: só passa o valor que bate exato.
    public static ToleranciaDeValor exata() {
        return new ToleranciaDeValor(BigDecimal.ZERO);
    }

    // Diz se a diferença está dentro da tolerância; compara só o número, então 0 e 0,00 contam igual.
    public boolean acomoda(BigDecimal diferenca) {
        if (diferenca == null) {
            throw new RegraInvalida("Não há diferença a comparar com a tolerância.");
        }
        return diferenca.abs().compareTo(quantia) <= 0;
    }
}
