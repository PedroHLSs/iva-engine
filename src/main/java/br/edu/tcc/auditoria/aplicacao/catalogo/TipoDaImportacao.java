package br.edu.tcc.auditoria.aplicacao.catalogo;

// Enum que diz se a importação trouxe os cinco arquivos obrigatórios ou só parte deles. Acrescentado em 04/10/2026 (D026).
public enum TipoDaImportacao {

    // Vieram os cinco arquivos obrigatórios: nada é herdado, nem o anexos-declarados.csv que não veio.
    COMPLETA,

    // Faltou algum dos cinco: o que não veio é copiado da carga mais recente, que fica intacta, e o resultado é uma carga nova.
    PARCIAL
}
