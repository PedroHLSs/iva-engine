package br.edu.tcc.auditoria.infraestrutura.sal;

/**
 * De onde o sal de instalação veio nesta subida.
 *
 * <p>Existe para que o diagnóstico possa responder "de onde saiu o sal que está
 * em uso" sem nunca mostrar o sal. Saber a origem é o que permite entender por
 * que a impressão digital mudou: um sal que veio de {@link #GERADO_AGORA} numa
 * máquina que já tinha acervo é um problema diferente de um que veio de
 * {@link #VARIAVEL_DE_AMBIENTE} com valor trocado.</p>
 */
public enum OrigemDoSal {

    /**
     * Propriedade de configuração {@code auditoria.pseudonimizacao.sal}, venha
     * ela de {@code application.properties}, de {@code -D} na linha de comando ou
     * de argumento do Spring.
     */
    PROPRIEDADE_DE_CONFIGURACAO("propriedade de configuração \"auditoria.pseudonimizacao.sal\""),

    /** Variável de ambiente {@code AUDITORIA_PSEUDONIMIZACAO_SAL}. */
    VARIAVEL_DE_AMBIENTE("variável de ambiente \"AUDITORIA_PSEUDONIMIZACAO_SAL\""),

    /** Arquivo local de configuração, fora do repositório. */
    ARQUIVO_LOCAL("arquivo local de configuração"),

    /**
     * Nenhuma das anteriores existia, e um sal aleatório foi criado e gravado
     * nesta subida.
     *
     * <p>É a única origem que representa uma decisão tomada pelo sistema, e não
     * por quem instalou. Por isso ela é anunciada no log em linha visível: quem
     * subiu o sistema precisa saber que passou a existir um segredo novo, e onde
     * ele está, para poder guardá-lo.</p>
     */
    GERADO_AGORA("gerado nesta subida");

    private final String descricao;

    OrigemDoSal(String descricao) {
        this.descricao = descricao;
    }

    /** Como a origem é escrita para quem lê o diagnóstico. */
    public String descricao() {
        return descricao;
    }
}
