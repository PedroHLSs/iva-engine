package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Evidência de um apontamento, gravada na tabela filha do apontamento.
 *
 * <p>Os dois valores são anuláveis, com significados distintos e deliberados:
 * {@code valorEncontrado} nulo é "o campo não veio no documento";
 * {@code valorEsperado} nulo é "a regra não tinha valor de referência a opor".
 * Texto vazio nessas colunas é outra coisa — campo que veio em branco no XML —,
 * e por isso não se troca nulo por string vazia em nenhum sentido.</p>
 *
 * <p>A origem é achatada em três colunas porque {@code OrigemEvidencia} é um tipo
 * selado com três variantes de formatos diferentes. {@code origemTipo} diz qual
 * variante é, e a reconstrução em {@code MapeadorDeAchado} volta ao tipo certo.</p>
 */
@Embeddable
class EvidenciaEmbutida {

    @Column(name = "campo_analisado", nullable = false)
    private String campoAnalisado;

    @Column(name = "valor_encontrado")
    private String valorEncontrado;

    @Column(name = "valor_esperado")
    private String valorEsperado;

    @Column(name = "origem_tipo", nullable = false)
    private String origemTipo;

    @Column(name = "origem_primeiro_termo", nullable = false)
    private String origemPrimeiroTermo;

    @Column(name = "origem_segundo_termo")
    private String origemSegundoTermo;

    protected EvidenciaEmbutida() {
        // Exigido pelo JPA.
    }

    EvidenciaEmbutida(
            String campoAnalisado,
            String valorEncontrado,
            String valorEsperado,
            String origemTipo,
            String origemPrimeiroTermo,
            String origemSegundoTermo) {
        this.campoAnalisado = campoAnalisado;
        this.valorEncontrado = valorEncontrado;
        this.valorEsperado = valorEsperado;
        this.origemTipo = origemTipo;
        this.origemPrimeiroTermo = origemPrimeiroTermo;
        this.origemSegundoTermo = origemSegundoTermo;
    }

    String campoAnalisado() {
        return campoAnalisado;
    }

    String valorEncontrado() {
        return valorEncontrado;
    }

    String valorEsperado() {
        return valorEsperado;
    }

    String origemTipo() {
        return origemTipo;
    }

    String origemPrimeiroTermo() {
        return origemPrimeiroTermo;
    }

    String origemSegundoTermo() {
        return origemSegundoTermo;
    }
}
