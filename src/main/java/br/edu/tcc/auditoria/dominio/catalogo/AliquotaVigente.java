package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.math.BigDecimal;

/**
 * Percentual que o catálogo atribui a um tributo, numa abrangência e numa
 * vigência.
 *
 * <p>Nenhum percentual está escrito no código, em constante, em valor padrão ou
 * em teste. Todos chegam por importação. Este tipo apenas guarda o que foi
 * importado, com {@link BigDecimal} e a escala declarada preservada.</p>
 *
 * <p>O percentual não é validado contra faixa nem sinal: fixar limites aqui
 * seria afirmar quanto a norma admite, e o catálogo precisa conseguir
 * representar até o que veio errado na carga para que isso seja apontável.</p>
 *
 * @param tributo     a que tributo o percentual se refere
 * @param percentual  percentual declarado pela fonte
 * @param abrangencia recorte a que o percentual se aplica
 * @param procedencia vigência e fonte
 */
public record AliquotaVigente(
        Tributo tributo,
        BigDecimal percentual,
        Abrangencia abrangencia,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    private static final String SEPARADOR_DE_CHAVE = "::";

    public AliquotaVigente {
        if (tributo == null) {
            throw new RegistroNormativoInvalido("A alíquota precisa dizer a que tributo se refere.");
        }
        if (abrangencia == null) {
            throw new RegistroNormativoInvalido(
                    "A alíquota de %s precisa de abrangência.".formatted(tributo));
        }
        if (percentual == null) {
            throw new RegistroNormativoInvalido(
                    "A alíquota de %s em \"%s\" precisa de percentual."
                            .formatted(tributo, abrangencia.valor()));
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "A alíquota de %s em \"%s\" precisa de vigência e fonte normativa."
                            .formatted(tributo, abrangencia.valor()));
        }
    }

    @Override
    public String chaveDeVigencia() {
        return chaveDe(tributo, abrangencia);
    }

    /** Chave da série temporal do par tributo e abrangência. */
    public static String chaveDe(Tributo tributo, Abrangencia abrangencia) {
        return tributo.name() + SEPARADOR_DE_CHAVE + abrangencia.valor();
    }
}
