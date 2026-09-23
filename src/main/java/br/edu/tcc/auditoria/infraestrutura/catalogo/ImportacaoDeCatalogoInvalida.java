package br.edu.tcc.auditoria.infraestrutura.catalogo;

public class ImportacaoDeCatalogoInvalida extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public ImportacaoDeCatalogoInvalida(String mensagem) {
        super(mensagem);
    }

    // Construtor que recebe a mensagem de erro e a causa original da falha.
    public ImportacaoDeCatalogoInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
