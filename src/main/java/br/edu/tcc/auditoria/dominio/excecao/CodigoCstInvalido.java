package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir um código de CST vazio ou malformado. */
public class CodigoCstInvalido extends ExcecaoDeDominio {

    public CodigoCstInvalido(String mensagem) {
        super(mensagem);
    }
}
