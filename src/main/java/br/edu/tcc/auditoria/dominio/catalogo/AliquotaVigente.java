package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.math.BigDecimal;

// Representa o percentual que o catálogo atribui a um tributo, numa abrangência e numa vigência; o percentual chega por importação e não é validado contra faixa.
public record AliquotaVigente(
        Tributo tributo,
        BigDecimal percentual,
        Abrangencia abrangencia,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    private static final String SEPARADOR_DE_CHAVE = "::";

    // Valida que a alíquota tenha tributo, abrangência, percentual e procedência.
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

    // Retorna a chave da série de vigência: o par tributo e abrangência.
    @Override
    public String chaveDeVigencia() {
        return chaveDe(tributo, abrangencia);
    }

    // Método estático que monta a chave da série do par tributo e abrangência.
    public static String chaveDe(Tributo tributo, Abrangencia abrangencia) {
        return tributo.name() + SEPARADOR_DE_CHAVE + abrangencia.valor();
    }
}
