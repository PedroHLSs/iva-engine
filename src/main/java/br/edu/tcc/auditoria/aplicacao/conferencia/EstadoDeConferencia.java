package br.edu.tcc.auditoria.aplicacao.conferencia;

// Enum com os quatro estados que a interface mostra; a ordem de declaração é a precedência, do mais forte ao mais fraco.
public enum EstadoDeConferencia {

    // O declarado no XML não corresponde ao tratamento que a regra indica; vem de severidade crítica, grave ou moderada.
    POSSIVEL_DIVERGENCIA(
            "Possível divergência",
            "O declarado no documento não corresponde ao que a regra aponta a partir da base "
                    + "normativa carregada."),

    // Situação que merece análise do responsável fiscal; vem de apontamento com severidade informativa.
    REQUER_CONFERENCIA(
            "Requer conferência",
            "A situação merece leitura de quem responde pelo fiscal. O sistema aponta o que "
                    + "encontrou e não conclui por você."),

    // Dados insuficientes no documento ou na base normativa carregada; corresponde a NAO_AVALIADO.
    NAO_FOI_POSSIVEL_CONCLUIR(
            "Não foi possível concluir",
            "Faltou dado no documento ou na base normativa carregada, e sem ele a verificação "
                    + "não pôde ser feita."),

    // Nenhuma violação das regras cadastradas, sobre os campos que elas alcançam; nunca escrever "Conferido" em tela nenhuma.
    SEM_DIVERGENCIA_IDENTIFICADA(
            "Sem divergência identificada",
            "As regras cadastradas foram aplicadas a este produto, sobre os campos que elas "
                    + "alcançam, e nenhuma encontrou violação.");

    private final String rotulo;
    private final String explicacao;

    // Construtor que associa a cada estado o rótulo da tela e a explicação em uma frase.
    EstadoDeConferencia(String rotulo, String explicacao) {
        this.rotulo = rotulo;
        this.explicacao = explicacao;
    }

    public String rotulo() {
        return rotulo;
    }

    public String explicacao() {
        return explicacao;
    }
}
