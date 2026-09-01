package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Medição de acurácia pedida sobre contagem que não fecha.
 *
 * <p>Contagem negativa, matriz cuja soma não bate com o total do gabarito,
 * comparação sem rótulo esperado. Não se confunde com métrica indefinida, que é
 * desfecho legítimo e se representa com {@code Metrica.Indefinida}: isto aqui é
 * defeito de quem montou a contagem.</p>
 */
public class AcuraciaInvalida extends ExcecaoDeDominio {

    public AcuraciaInvalida(String mensagem) {
        super(mensagem);
    }
}
