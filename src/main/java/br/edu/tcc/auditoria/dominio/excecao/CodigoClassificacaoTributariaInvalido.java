package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir um código de classificação tributária vazio ou malformado. */
public class CodigoClassificacaoTributariaInvalido extends ExcecaoDeDominio {

    public CodigoClassificacaoTributariaInvalido(String mensagem) {
        super(mensagem);
    }
}
