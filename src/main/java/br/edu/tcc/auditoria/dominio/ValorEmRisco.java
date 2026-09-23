package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.ValorEmRiscoInvalido;

import java.math.BigDecimal;
import java.util.Optional;

// Interface que guarda o valor em risco de um apontamento: ou o valor calculado, ou o motivo de não dar para calcular. O motivo é obrigatório, para o vazio nunca ficar sem explicação.
public sealed interface ValorEmRisco {

    // Retorna o valor, ou vazio quando não dá para calcular.
    Optional<BigDecimal> valor();

    // Retorna o motivo de não haver valor, ou vazio quando há valor.
    Optional<String> motivoDaAusencia();

    // Cria o valor em risco calculado.
    static ValorEmRisco calculado(BigDecimal quantia) {
        return new Calculado(quantia);
    }

    // Cria o valor em risco que não dá para calcular, com o motivo.
    static ValorEmRisco naoCalculavel(String motivo) {
        return new NaoCalculavel(motivo);
    }

    // Representa o valor calculado; mantém as casas decimais, então compare com compareTo, não com equals.
    record Calculado(BigDecimal quantia) implements ValorEmRisco {

        // Valida que o valor calculado exista.
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

    // Representa o apontamento sem valor calculável, com o motivo.
    record NaoCalculavel(String motivo) implements ValorEmRisco {

        // Valida que o motivo esteja preenchido.
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
