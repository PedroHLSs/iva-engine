package br.edu.tcc.auditoria.aplicacao.catalogo;

// Enum que indica a procedência de uma tabela da carga, declarada linha a linha no CSV: dado normativo ou dado fictício de demonstração.
public enum Natureza {

    // Dado inventado para demonstração, que não pode ser lido como afirmação sobre a lei.
    FICTICIO("fictício"),

    // Dado transcrito de fonte normativa por quem montou a carga.
    NORMATIVO("normativo");

    private final String rotulo;

    // Construtor que associa a cada natureza o rótulo exibido na tela.
    Natureza(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
