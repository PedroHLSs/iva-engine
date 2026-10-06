package br.edu.tcc.auditoria.infraestrutura.catalogo;

public class RecusaDeCampo extends ImportacaoDeCatalogoInvalida {

    private final int linha;
    private final String coluna;
    private final String valor;

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RecusaDeCampo(int linha, String coluna, String valor, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.linha = linha;
        this.coluna = coluna;
        this.valor = valor;
    }

    public int linha() {
        return linha;
    }

    public String coluna() {
        return coluna;
    }

    public String valor() {
        return valor;
    }
}
