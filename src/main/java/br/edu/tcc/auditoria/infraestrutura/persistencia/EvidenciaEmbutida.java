package br.edu.tcc.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// Representa uma evidência do apontamento, gravada na tabela filha. valorEncontrado null quer dizer que o campo não veio na nota, e valorEsperado null que a regra não tinha referência; texto vazio é outra coisa. A origem fica em três colunas, e o MapeadorDeAchado volta ao tipo certo.
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

    // Construtor vazio exigido pelo JPA.
    protected EvidenciaEmbutida() {
    }

    // Construtor que recebe todos os campos da evidência.
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
