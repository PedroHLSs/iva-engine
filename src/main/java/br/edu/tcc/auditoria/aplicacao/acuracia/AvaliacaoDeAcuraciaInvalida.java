package br.edu.tcc.auditoria.aplicacao.acuracia;

/**
 * Medição de acurácia pedida sobre insumos que não permitem medir.
 *
 * <p>Gabarito com a mesma linha duas vezes, gabarito citando regra que o
 * conjunto não tem, relatório sem destino. São defeitos de quem montou a
 * medição, e não resultados dela.</p>
 *
 * <p>Não se confunde com métrica indefinida, que é resultado legítimo e se
 * representa com {@code Metrica.Indefinida}.</p>
 */
public class AvaliacaoDeAcuraciaInvalida extends RuntimeException {

    public AvaliacaoDeAcuraciaInvalida(String mensagem) {
        super(mensagem);
    }
}
