package br.edu.tcc.auditoria.dominio.excecao;

public class PeriodoVigenciaInvalido extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public PeriodoVigenciaInvalido(String mensagem) {
        super(mensagem);
    }
}
