package br.edu.tcc.auditoria.infraestrutura.catalogo;

import java.util.List;

public class CargaRecusada extends ImportacaoDeCatalogoInvalida {

    private final transient List<LinhaRecusada> recusadas;

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    public CargaRecusada(String mensagem, List<LinhaRecusada> recusadas) {
        super(mensagem);
        this.recusadas = List.copyOf(recusadas);
    }

    public List<LinhaRecusada> recusadas() {
        return recusadas;
    }
}
