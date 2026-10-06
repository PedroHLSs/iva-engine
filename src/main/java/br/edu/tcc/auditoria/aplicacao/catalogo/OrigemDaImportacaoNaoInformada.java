package br.edu.tcc.auditoria.aplicacao.catalogo;

// Exceção da importação parcial que não disse de qual carga herdar, ou não disse os instantes que a tela mostrou dela. Leva a carga mais recente, para quem pediu ver de onde as tabelas viriam antes de enviar de novo. Acrescentada em 04/10/2026 (D026).
public class OrigemDaImportacaoNaoInformada extends RuntimeException {

    private final transient EstadoDaCarga origemAtual;

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public OrigemDaImportacaoNaoInformada(String mensagem, EstadoDaCarga origemAtual) {
        super(mensagem);
        this.origemAtual = origemAtual;
    }

    public EstadoDaCarga origemAtual() {
        return origemAtual;
    }
}
