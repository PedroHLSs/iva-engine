package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir um CFOP fora do formato exigido. */
public class CfopInvalido extends ExcecaoDeDominio {

    public CfopInvalido(String mensagem) {
        super(mensagem);
    }
}
