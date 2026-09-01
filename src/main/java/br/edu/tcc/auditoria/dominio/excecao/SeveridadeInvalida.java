package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza comparação de gravidade contra uma severidade não informada. */
public class SeveridadeInvalida extends ExcecaoDeDominio {

    public SeveridadeInvalida(String mensagem) {
        super(mensagem);
    }
}
