package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.RegraInvalida;

import java.math.BigDecimal;

/**
 * Diferença absoluta que uma regra de valor deixa passar sem apontar.
 *
 * <p>Existe porque o valor recalculado a partir da base e da alíquota quase
 * nunca bate dígito a dígito com o declarado: quem emitiu o documento arredondou
 * em algum ponto, e o critério de arredondamento não é algo que este projeto
 * afirme. Sem tolerância, a regra apontaria centavos como incoerência e o
 * relatório ficaria inútil de tão ruidoso.</p>
 *
 * <p>A tolerância é escolha de quem opera a auditoria, não conteúdo normativo, e
 * por isso é parâmetro e não constante. Zero é aceito e significa exigir
 * igualdade de grandeza.</p>
 *
 * @param quantia diferença absoluta admitida, na mesma unidade dos valores comparados
 */
public record ToleranciaDeValor(BigDecimal quantia) {

    public ToleranciaDeValor {
        if (quantia == null) {
            throw new RegraInvalida("A tolerância precisa de uma quantia. Para exigir igualdade exata, use zero.");
        }
        if (quantia.signum() < 0) {
            throw new RegraInvalida(
                    "A tolerância não pode ser negativa, mas veio %s.".formatted(quantia.toPlainString()));
        }
    }

    public static ToleranciaDeValor de(BigDecimal quantia) {
        return new ToleranciaDeValor(quantia);
    }

    /** Tolerância zero: só passa quem bate em grandeza. */
    public static ToleranciaDeValor exata() {
        return new ToleranciaDeValor(BigDecimal.ZERO);
    }

    /**
     * Indica se a diferença cabe na tolerância.
     *
     * <p>Comparação por {@code compareTo}, nunca por {@code equals}: a escala
     * declarada é informação preservada em outros pontos do modelo, mas aqui o
     * que interessa é a grandeza, e {@code 0} e {@code 0,00} são a mesma
     * grandeza.</p>
     */
    public boolean acomoda(BigDecimal diferenca) {
        if (diferenca == null) {
            throw new RegraInvalida("Não há diferença a comparar com a tolerância.");
        }
        return diferenca.abs().compareTo(quantia) <= 0;
    }
}
