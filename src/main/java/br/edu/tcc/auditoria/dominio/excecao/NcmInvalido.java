package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir um NCM fora do formato exigido. */
public class NcmInvalido extends ExcecaoDeDominio {

    public NcmInvalido(String mensagem) {
        super(mensagem);
    }
}
