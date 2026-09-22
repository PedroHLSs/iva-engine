package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;

import java.math.BigDecimal;

/**
 * Um percentual que a carga atribui a um tributo, numa abrangência, na data do
 * documento.
 *
 * <p>O percentual é repassado como {@link BigDecimal}, com a escala que a carga
 * declarou. Quem o formata é a borda da API, em texto — arredondar aqui apagaria
 * a diferença entre um percentual declarado com duas casas e um declarado com
 * quatro, que são afirmações diferentes da fonte.</p>
 */
public record AliquotaDoCatalogo(
        String abrangencia, BigDecimal percentual, ReferenciaNormativa referencia) {

    public AliquotaDoCatalogo {
        if (abrangencia == null || abrangencia.isBlank()) {
            throw new ConferenciaInvalida(
                    "A alíquota precisa dizer a que abrangência ela se aplica: um percentual solto não "
                            + "é conferível.");
        }
        if (percentual == null) {
            throw new ConferenciaInvalida("A alíquota do catálogo sempre traz percentual.");
        }
        if (referencia == null) {
            throw new ConferenciaInvalida("A alíquota precisa da vigência e da fonte.");
        }
    }

    public static AliquotaDoCatalogo de(AliquotaVigente vigente) {
        if (vigente == null) {
            throw new ConferenciaInvalida("Não há alíquota a apresentar.");
        }
        return new AliquotaDoCatalogo(
                vigente.abrangencia().valor(),
                vigente.percentual(),
                ReferenciaNormativa.de(vigente.procedencia()));
    }
}
