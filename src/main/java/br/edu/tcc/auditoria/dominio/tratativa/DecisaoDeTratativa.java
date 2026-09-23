package br.edu.tcc.auditoria.dominio.tratativa;

// Enum com as duas decisões que uma pessoa pode registrar sobre um apontamento; nenhuma delas apaga o apontamento.
public enum DecisaoDeTratativa {

    // O auditor concorda com o apontamento: há erro na nota.
    ACEITO,

    // O auditor diz que a nota está certa e o apontamento não procede.
    REFUTADO
}
