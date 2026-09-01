package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.AcuraciaInvalida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * O valor de uma métrica, ou a declaração de que ela não tem valor.
 *
 * <p>Tipo selado em duas variantes pelo mesmo motivo que {@code Avaliacao} é
 * selado em três: para tornar impossível o estado que este projeto mais teme —
 * aqui, uma métrica sem denominador que se apresenta como número.</p>
 *
 * <h2>Indefinida não é zero, e muito menos um</h2>
 *
 * <p>Precisão é VP dividido por VP mais FP. Quando o motor não apontou nada que
 * tenha sido medido, esse denominador é zero. Devolver 1 diria "de tudo o que o
 * sistema apontou, tudo procedia" a respeito de um sistema que não apontou nada;
 * devolver 0 diria o contrário, e seria igualmente falso. A resposta correta é
 * que a pergunta não tem resposta com os dados que há, e {@link Indefinida} a dá
 * junto do motivo, para que quem lê o relatório saiba qual denominador
 * faltou.</p>
 *
 * <p>É a razão de este tipo existir em vez de um {@code BigDecimal}: um
 * {@code BigDecimal} obrigaria alguém, em algum ponto, a escolher um número para
 * o caso sem denominador.</p>
 *
 * <h2>Escala fixa em quatro casas</h2>
 *
 * <p>Toda métrica definida é arredondada a quatro casas decimais, com
 * {@link RoundingMode#HALF_UP}. Quatro casas porque o gabarito tem centenas de
 * linhas, não milhões: a quinta casa seria precisão inventada sobre uma amostra
 * que não a sustenta.</p>
 */
public sealed interface Metrica {

    /** Casas decimais de toda métrica definida. */
    int ESCALA = 4;

    /** O valor, vazio quando a métrica é indefinida. */
    Optional<BigDecimal> valor();

    /** Por que não há valor; vazio quando a métrica é definida. */
    Optional<String> motivoDaIndefinicao();

    /** Indica se há valor. */
    default boolean estaDefinida() {
        return valor().isPresent();
    }

    /** Métrica com valor, arredondado à escala do tipo. */
    static Metrica definida(BigDecimal valor) {
        return new Definida(valor);
    }

    /** Métrica sem valor, com o motivo registrado. */
    static Metrica indefinida(String motivo) {
        return new Indefinida(motivo);
    }

    /**
     * Divide guardando a escala, ou declara a métrica indefinida se o
     * denominador for zero.
     *
     * <p>Concentra num lugar só a única forma pela qual uma métrica deste
     * sistema pode nascer de uma divisão. Enquanto ninguém dividir por fora, não
     * há como um denominador zero virar número.</p>
     *
     * @param motivoSeSemDenominador o que dizer quando o denominador for zero
     */
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

    /**
     * Métrica com valor.
     *
     * <p>O componente se chama {@code proporcao}, e não {@code valor}, porque
     * {@link Metrica#valor()} é o acessor comum às duas variantes e devolve
     * {@link Optional}. Os dois nomes não podem coincidir — e o que sobra
     * descreve melhor o que se guarda: precisão, recall, F1 e cobertura são
     * proporções, todas entre zero e um.</p>
     */
    record Definida(BigDecimal proporcao) implements Metrica {

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

    /**
     * Métrica sem valor, com o motivo.
     *
     * <p>O motivo é obrigatório e não vazio, pelo mesmo raciocínio de
     * {@code Avaliacao.NaoAvaliada}: uma lacuna sem explicação no relatório é
     * lida como zero por quem tiver pressa.</p>
     */
    record Indefinida(String motivo) implements Metrica {

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
