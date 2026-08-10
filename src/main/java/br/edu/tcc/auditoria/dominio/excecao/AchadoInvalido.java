package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza apontamento sem identificação de regra, sem evidência ou sem vigência. */
public class AchadoInvalido extends ExcecaoDeDominio {

    public AchadoInvalido(String mensagem) {
        super(mensagem);
    }
}
