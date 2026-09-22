package br.edu.tcc.auditoria.aplicacao.conferencia;

/**
 * A versão da regra que produziu uma verificação — ou o motivo de não se saber.
 *
 * <h2>Por que às vezes não se sabe</h2>
 *
 * <p>O banco não guarda avaliação conforme. A Etapa 5 grava apontamento, a
 * Etapa 6 acrescentou as não concluídas, e a regra que se aplicou por inteiro e
 * nada encontrou <strong>não deixa linha nenhuma</strong> — é exatamente por
 * isso que o harness da D008 roda o motor de novo em vez de ler o banco, e por
 * isso que a D009 deriva o conforme com a conta impressa ao lado.</p>
 *
 * <p>Consequência direta: para uma verificação sem divergência, a versão da
 * regra não está gravada em lugar nenhum. Ela poderia ser inferida da versão do
 * conjunto, que a execução registra, mas inferir é afirmar — e a afirmação
 * dependeria de o código de hoje ainda montar o mesmo conjunto de então. Em vez
 * disso a ausência é dita, com o motivo.</p>
 *
 * <p>É a mesma forma de {@code ValorEmRisco}: ou há o valor, ou há a explicação
 * de não haver, e é impossível construir um dos dois sem o outro.</p>
 */
public sealed interface VersaoDaRegra {

    /** O texto para a tela: a versão, ou a frase que a substitui. */
    String paraLeitura();

    static VersaoDaRegra registrada(String valor) {
        return new Registrada(valor);
    }

    static VersaoDaRegra naoRegistrada(String motivo) {
        return new NaoRegistrada(motivo);
    }

    /**
     * A versão veio de uma linha gravada: um apontamento ou uma avaliação não
     * concluída, que são as duas coisas que o banco guarda por (item, regra).
     */
    record Registrada(String valor) implements VersaoDaRegra {

        public Registrada {
            if (valor == null || valor.isBlank()) {
                throw new ConferenciaInvalida(
                        "Uma versão registrada precisa do valor. Se não há, use "
                                + "VersaoDaRegra.naoRegistrada(motivo).");
            }
        }

        @Override
        public String paraLeitura() {
            return valor;
        }
    }

    /** Não há linha gravada de onde tirá-la, e o motivo acompanha. */
    record NaoRegistrada(String motivo) implements VersaoDaRegra {

        public NaoRegistrada {
            if (motivo == null || motivo.isBlank()) {
                throw new ConferenciaInvalida(
                        "Versão de regra ausente precisa registrar o motivo: campo em branco é "
                                + "indistinguível de campo que ninguém preencheu.");
            }
        }

        @Override
        public String paraLeitura() {
            return "(não registrada)";
        }
    }
}
