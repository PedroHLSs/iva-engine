package br.edu.tcc.auditoria.infraestrutura.sal;

// Enum que diz de onde veio o sal nesta subida, para o diagnóstico explicar uma troca de impressão digital sem mostrar o sal.
public enum OrigemDoSal {

    // Veio da propriedade auditoria.pseudonimizacao.sal: do application.properties, de -D ou de argumento do Spring.
    PROPRIEDADE_DE_CONFIGURACAO("propriedade de configuração \"auditoria.pseudonimizacao.sal\""),

    // Veio da variável de ambiente AUDITORIA_PSEUDONIMIZACAO_SAL.
    VARIAVEL_DE_AMBIENTE("variável de ambiente \"AUDITORIA_PSEUDONIMIZACAO_SAL\""),

    // Veio do arquivo local de configuração, fora do repositório.
    ARQUIVO_LOCAL("arquivo local de configuração"),

    // Nada estava configurado, e um sal novo foi sorteado e gravado nesta subida. É avisado no log, porque quem subiu precisa guardar esse arquivo.
    GERADO_AGORA("gerado nesta subida");

    private final String descricao;

    // Construtor que recebe o texto da origem.
    OrigemDoSal(String descricao) {
        this.descricao = descricao;
    }

    // Retorna a origem escrita para quem lê o diagnóstico.
    public String descricao() {
        return descricao;
    }
}
