package br.edu.tcc.auditoria.aplicacao.analise;

/** Acervo de análise montado sem o que ele precisa para ser gravado ou lido. */
public class AnaliseInvalida extends RuntimeException {

    public AnaliseInvalida(String mensagem) {
        super(mensagem);
    }
}
