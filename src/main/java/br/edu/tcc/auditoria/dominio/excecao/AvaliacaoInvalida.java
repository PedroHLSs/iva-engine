package br.edu.tcc.auditoria.dominio.excecao;

/** Tentativa de construir uma avaliação sem os dados que a tornam conferível. */
public class AvaliacaoInvalida extends ExcecaoDeDominio {

    public AvaliacaoInvalida(String mensagem) {
        super(mensagem);
    }
}
