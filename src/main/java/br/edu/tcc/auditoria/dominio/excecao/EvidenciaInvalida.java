package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza evidência sem campo analisado ou sem origem rastreável. */
public class EvidenciaInvalida extends ExcecaoDeDominio {

    public EvidenciaInvalida(String mensagem) {
        super(mensagem);
    }
}
