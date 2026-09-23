package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

// Interface selada que representa o valor de uma métrica, ou a declaração de que ela não tem valor; indefinida não é zero nem um.
public sealed interface Metrica {

    // Casas decimais de toda métrica definida, arredondada com HALF_UP.
    int ESCALA = 4;

    // Retorna o valor, ou vazio quando a métrica é indefinida.
    Optional<BigDecimal> valor();

    // Retorna o motivo de não haver valor, ou vazio quando a métrica é definida.
    Optional<String> motivoDaIndefinicao();

    // Indica se a métrica tem valor.
    default boolean estaDefinida() {
        return valor().isPresent();
    }

    // Cria uma métrica com valor, arredondado à escala do tipo.
    static Metrica definida(BigDecimal valor) {
        return new Definida(valor);
    }

    // Cria uma métrica sem valor, com o motivo registrado.
    static Metrica indefinida(String motivo) {
        return new Indefinida(motivo);
    }

    // Divide mantendo a escala, ou declara a métrica indefinida se o denominador for zero; é o único lugar em que uma métrica nasce de uma divisão.
    static Metrica razao(long numerador, long denominador, String motivoSeSemDenominador) {
        if (denominador < 0 || numerador < 0) {
            throw new AcuraciaInvalida(
                    "Métrica não se calcula com contagem negativa: %d dividido por %d."
                            .formatted(numerador, denominador));
        }
        if (denominador == 0) {
            return indefinida(motivoSeSemDenominador);
        }
        return definida(BigDecimal.valueOf(numerador)
                .divide(BigDecimal.valueOf(denominador), ESCALA, RoundingMode.HALF_UP));
    }

    // Representa a métrica com valor, guardado como proporção entre zero e um.
    record Definida(BigDecimal proporcao) implements Metrica {

        // Valida que a proporção exista, arredonda à escala e recusa valor fora de 0 a 1.
        public Definida {
            if (proporcao == null) {
                throw new AcuraciaInvalida(
                        "Uma métrica definida precisa do valor. Para métrica sem denominador use "
                                + "Metrica.indefinida(motivo).");
            }
            proporcao = proporcao.setScale(ESCALA, RoundingMode.HALF_UP);
            if (proporcao.compareTo(BigDecimal.ZERO) < 0 || proporcao.compareTo(BigDecimal.ONE) > 0) {
                throw new AcuraciaInvalida(
                        ("Métrica fora de 0 a 1: %s. Precisão, recall, F1 e cobertura são proporções, e "
                                + "um valor fora dessa faixa é erro de contagem, não resultado.")
                                .formatted(proporcao.toPlainString()));
            }
        }

        @Override
        public Optional<BigDecimal> valor() {
            return Optional.of(proporcao);
        }

        @Override
        public Optional<String> motivoDaIndefinicao() {
            return Optional.empty();
        }
    }

    // Representa a métrica sem valor, com o motivo obrigatório.
    record Indefinida(String motivo) implements Metrica {

        // Valida que a métrica indefinida traga o motivo.
        public Indefinida {
            if (motivo == null || motivo.isBlank()) {
                throw new AcuraciaInvalida(
                        "Uma métrica indefinida precisa registrar por que não há valor: sem isso ela é "
                                + "indistinguível de zero para quem lê o relatório.");
            }
        }

        @Override
        public Optional<BigDecimal> valor() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaIndefinicao() {
            return Optional.of(motivo);
        }
    }
}
