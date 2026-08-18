package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Regra de auditoria mal configurada ou aplicada sem os argumentos que precisa.
 *
 * <p>Não se confunde com {@code NAO_AVALIADO}: aquilo é desfecho normal de uma
 * regra bem construída que não teve dado para julgar; isto é defeito de quem
 * montou a regra ou a chamou.</p>
 */
public class RegraInvalida extends ExcecaoDeDominio {

    public RegraInvalida(String mensagem) {
        super(mensagem);
    }
}
