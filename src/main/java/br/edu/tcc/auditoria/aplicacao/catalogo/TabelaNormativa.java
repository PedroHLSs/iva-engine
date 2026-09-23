package br.edu.tcc.auditoria.aplicacao.catalogo;

// Enum que lista as tabelas normativas cuja cobertura precisa ser declarada na importação; a de alíquotas fica de fora porque a regra de valor já responde não avaliado sozinha.
public enum TabelaNormativa {

    // Códigos de classificação tributária e os CSTs que cada um admite.
    CLASSIFICACAO_TRIBUTARIA,

    // Registros de NCM existentes.
    NCM,

    // Vínculo entre NCM e anexo, com o tipo de tratamento.
    ITEM_ANEXO
}
