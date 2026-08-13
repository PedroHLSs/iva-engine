package br.edu.tcc.auditoria.dominio.catalogo;

/**
 * Qual tributo, e em qual esfera, uma alíquota do catálogo se refere.
 *
 * <p>São rótulos de eixo, não conteúdo normativo: nenhum percentual, faixa,
 * vigência ou regra está associado a estas constantes. Os valores chegam por
 * importação, em {@link AliquotaVigente}.</p>
 */
public enum Tributo {

    /** Parcela estadual do IBS. */
    IBS_UF,

    /** Parcela municipal do IBS. */
    IBS_MUN,

    /** CBS. */
    CBS
}
