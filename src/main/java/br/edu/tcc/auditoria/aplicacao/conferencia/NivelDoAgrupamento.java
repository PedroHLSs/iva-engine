package br.edu.tcc.auditoria.aplicacao.conferencia;

// Enum que indica quanto da chave de agrupamento o documento declarou; o nível vai escrito no grupo.
public enum NivelDoAgrupamento {

    // O documento declarou os dois.
    NCM_E_CLASSTRIB("agrupado por NCM e cClassTrib, os dois declarados no documento"),

    // Declarou NCM, não declarou cClassTrib.
    SOMENTE_NCM("agrupado só por NCM: o documento não declarou cClassTrib para estes itens"),

    // Declarou cClassTrib, não declarou NCM.
    SOMENTE_CLASSTRIB("agrupado só por cClassTrib: o documento não declarou NCM para estes itens"),

    // Não declarou nenhum dos dois.
    SEM_NENHUM_DOS_DOIS(
            "não foi possível agrupar por enquadramento: o documento não declarou NCM nem cClassTrib "
                    + "para estes itens. Eles continuam na lista porque sumir seria pior");

    private final String rotulo;

    // Construtor que associa a cada nível a frase exibida na tela.
    NivelDoAgrupamento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    // Método estático que retorna o nível correspondente ao que o documento declarou.
    public static NivelDoAgrupamento de(boolean temNcm, boolean temClassTrib) {
        if (temNcm && temClassTrib) {
            return NCM_E_CLASSTRIB;
        }
        if (temNcm) {
            return SOMENTE_NCM;
        }
        if (temClassTrib) {
            return SOMENTE_CLASSTRIB;
        }
        return SEM_NENHUM_DOS_DOIS;
    }
}
