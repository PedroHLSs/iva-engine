package br.edu.tcc.auditoria.aplicacao.auditoria;

/**
 * Auditoria pedida sem os insumos que ela exige.
 *
 * <p>Não se confunde com {@code NAO_AVALIADO}, que é desfecho legítimo de regra
 * sem dado. Isto é defeito de quem montou a chamada — documento nulo, conjunto
 * de regras ausente, contexto normativo que não foi construído.</p>
 */
public class AuditoriaInvalida extends RuntimeException {

    public AuditoriaInvalida(String mensagem) {
        super(mensagem);
    }
}
