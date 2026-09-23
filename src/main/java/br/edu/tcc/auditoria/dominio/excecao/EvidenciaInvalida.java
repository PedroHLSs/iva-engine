package br.edu.tcc.auditoria.dominio.excecao;

public class EvidenciaInvalida extends ExcecaoDeDominio {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public EvidenciaInvalida(String mensagem) {
        super(mensagem);
    }
}
