package br.edu.tcc.auditoria.aplicacao.auditoria;

// Enum que diz de onde veio a tolerância de valor da R05 usada numa execução (D023): do padrão do sistema, porque a instalação não configurou outra, ou da configuração da instalação. O padrão aparece escrito, nunca implícito.
public enum OrigemDaTolerancia {

    // A instalação não definiu a tolerância, e valeu o padrão declarado no application.properties.
    PADRAO("padrão do sistema; a instalação não configurou outra"),

    // A instalação definiu a tolerância, por propriedade, variável de ambiente ou linha de comando.
    CONFIGURADA("configurada na instalação");

    private final String rotulo;

    // Construtor que associa o rótulo por extenso.
    OrigemDaTolerancia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
