package br.edu.tcc.auditoria.aplicacao.catalogo;

/**
 * As tabelas normativas cuja cobertura precisa ser declarada na importação.
 *
 * <p>Existe para que o nome de cada tabela seja o mesmo no arquivo de cobertura,
 * na coluna do banco e na mensagem de erro. Estes nomes identificam tabelas do
 * sistema; nenhum deles é conteúdo da legislação.</p>
 *
 * <p>A tabela de alíquotas não está aqui de propósito: quando o catálogo não
 * traz alíquota vigente na data, a regra que confere valor já responde "não
 * avaliado" por conta própria, sem precisar de cobertura declarada para separar
 * silêncio de ausência de carga.</p>
 */
public enum TabelaNormativa {

    /** Códigos de classificação tributária e os CSTs que cada um admite. */
    CLASSIFICACAO_TRIBUTARIA,

    /** Registro de NCM existentes. */
    NCM,

    /** Vínculo entre NCM e anexo, com o tipo de tratamento. */
    ITEM_ANEXO
}
