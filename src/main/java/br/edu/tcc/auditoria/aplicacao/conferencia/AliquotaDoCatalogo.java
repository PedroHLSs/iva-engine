package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;

import java.math.BigDecimal;

// Representa um percentual que a carga atribui a um tributo, numa abrangência, mantendo a escala declarada.
public record AliquotaDoCatalogo(
        String abrangencia, BigDecimal percentual, ReferenciaNormativa referencia) {

    // Valida que a alíquota tenha abrangência, percentual e referência normativa.
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

    // Método estático que cria a alíquota de exibição a partir da alíquota vigente do catálogo.
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
