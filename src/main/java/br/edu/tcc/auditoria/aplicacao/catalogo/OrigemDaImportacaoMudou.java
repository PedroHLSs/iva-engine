package br.edu.tcc.auditoria.aplicacao.catalogo;

// Exceção da importação parcial cuja origem não é mais a que a tela mostrou: outra carga passou a ser a mais recente, ou a mesma versão foi alterada, ou excluída e importada de novo. Nada é gravado. Leva a origem atual. Acrescentada em 04/10/2026 (D026).
public class OrigemDaImportacaoMudou extends RuntimeException {

    private final transient EstadoDaCarga origemAtual;

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public OrigemDaImportacaoMudou(String mensagem, EstadoDaCarga origemAtual) {
        super(mensagem);
        this.origemAtual = origemAtual;
    }

    public EstadoDaCarga origemAtual() {
        return origemAtual;
    }
}
