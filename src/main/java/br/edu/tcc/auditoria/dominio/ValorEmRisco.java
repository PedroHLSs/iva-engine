package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ValorEmRiscoInvalido;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Montante associado a um {@link Achado}, quando é possível quantificá-lo.
 *
 * <p>Nem todo apontamento tem valor calculável: uma combinação inválida de
 * códigos, ou um campo ausente, não produzem diferença monetária aferível. O
 * tipo é selado em duas variantes justamente para que a ausência nunca fique
 * sem explicação — {@link NaoCalculavel} exige o motivo no construtor, de modo
 * que é impossível esquecer de registrá-lo.</p>
 *
 * <p>Um {@code Optional<BigDecimal>} solto permitiria o vazio silencioso; aqui
 * o vazio sempre vem acompanhado da razão, e {@link #valor()} continua
 * entregando o {@code Optional<BigDecimal>} para quem só precisa do número.</p>
 */
public sealed interface ValorEmRisco {

    /** O montante, quando calculável; vazio caso contrário. */
    Optional<BigDecimal> valor();

    /** O motivo de não haver montante, quando não há; vazio caso contrário. */
    Optional<String> motivoDaAusencia();

    /** Apontamento com montante aferido. */
    static ValorEmRisco calculado(BigDecimal quantia) {
        return new Calculado(quantia);
    }

    /** Apontamento sem montante aferível, com o motivo registrado. */
    static ValorEmRisco naoCalculavel(String motivo) {
        return new NaoCalculavel(motivo);
    }

    /**
     * Montante aferido para o apontamento.
     *
     * <p>A escala é preservada como calculada; comparações de grandeza devem
     * usar {@code compareTo}, não {@code equals}.</p>
     */
    record Calculado(BigDecimal quantia) implements ValorEmRisco {

        public Calculado {
            if (quantia == null) {
                throw new ValorEmRiscoInvalido(
                        "Um valor em risco calculado precisa de quantia. "
                                + "Se não há quantia, use ValorEmRisco.naoCalculavel(motivo).");
            }
        }

        @Override
        public Optional<BigDecimal> valor() {
            return Optional.of(quantia);
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.empty();
        }
    }

    /**
     * Apontamento cujo montante não é aferível.
     *
     * @param motivo por que não há valor a calcular
     */
    record NaoCalculavel(String motivo) implements ValorEmRisco {

        public NaoCalculavel {
            if (motivo == null || motivo.isBlank()) {
                throw new ValorEmRiscoInvalido(
                        "Um valor em risco não calculável precisa registrar o motivo da ausência.");
            }
        }

        @Override
        public Optional<BigDecimal> valor() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.of(motivo);
        }
    }
}
