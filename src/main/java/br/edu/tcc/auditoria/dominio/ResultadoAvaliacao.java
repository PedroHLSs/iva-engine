package br.edu.tcc.auditoria.dominio;

// Enum com os três resultados possíveis de uma regra. NAO_AVALIADO existe para o relatório não mentir: faltar dado é diferente de estar tudo certo.
public enum ResultadoAvaliacao {

    // A regra encontrou problema e gerou um apontamento.
    ACHADO,

    // A regra rodou inteira e não encontrou problema.
    CONFORME,

    // A regra não conseguiu avaliar: faltou campo na nota, faltou tabela, ou a data está fora do período. Nunca deve ser mostrado como conformidade.
    NAO_AVALIADO
}
