package br.edu.tcc.auditoria.infraestrutura.catalogo;

public class RecusaDeCabecalho extends ImportacaoDeCatalogoInvalida {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public RecusaDeCabecalho(String mensagem) {
        super(mensagem);
    }
}
