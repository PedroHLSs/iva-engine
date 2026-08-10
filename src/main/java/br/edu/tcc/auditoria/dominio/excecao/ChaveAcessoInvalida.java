package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir uma chave de acesso fora do formato exigido. */
public class ChaveAcessoInvalida extends ExcecaoDeDominio {

    public ChaveAcessoInvalida(String mensagem) {
        super(mensagem);
    }
}
