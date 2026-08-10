package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza período de vigência sem início ou com fim anterior ao início. */
public class PeriodoVigenciaInvalido extends ExcecaoDeDominio {

    public PeriodoVigenciaInvalido(String mensagem) {
        super(mensagem);
    }
}
