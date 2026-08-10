package br.edu.tcc.auditoria.dominio.excecao;

/**
 * Sinaliza valor em risco malformado.
 *
 * <p>Em particular, a tentativa de declarar um valor como não calculável sem
 * dizer por quê.</p>
 */
public class ValorEmRiscoInvalido extends ExcecaoDeDominio {

    public ValorEmRiscoInvalido(String mensagem) {
        super(mensagem);
    }
}
