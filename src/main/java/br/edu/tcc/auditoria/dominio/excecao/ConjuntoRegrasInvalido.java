package br.edu.tcc.auditoria.dominio.excecao;

/** Conjunto de regras sem versão, vazio, ou com identificadores repetidos. */
public class ConjuntoRegrasInvalido extends ExcecaoDeDominio {

    public ConjuntoRegrasInvalido(String mensagem) {
        super(mensagem);
    }
}
