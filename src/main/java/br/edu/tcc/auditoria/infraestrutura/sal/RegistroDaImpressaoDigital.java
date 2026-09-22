package br.edu.tcc.auditoria.infraestrutura.sal;

import java.util.Optional;

/**
 * A impressão digital do sal que produziu o que já está gravado.
 *
 * <p>Interface aqui e implementação em {@code infraestrutura.persistencia}
 * porque quem pergunta — o guarda da subida, em {@code infraestrutura.cli} — não
 * pode alcançar as entidades JPA, que são de visibilidade de pacote. A porta
 * mantém o guarda testável sem banco.</p>
 */
public interface RegistroDaImpressaoDigital {

    /** A impressão digital gravada, se já houver uma. */
    Optional<Registro> registrada();

    /** Grava, ou substitui, a impressão digital da instalação. */
    void registrar(ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente);

    /**
     * O que está gravado.
     *
     * @param sobreAcervoExistente verdadeiro quando o registro foi feito sobre um
     *                             acervo que já tinha documentos, isto é, quando
     *                             não houve o que verificar. O diagnóstico
     *                             precisa disso para não afirmar uma conferência
     *                             que não aconteceu.
     */
    record Registro(ImpressaoDigitalDoSal impressao, OrigemDoSal origem, boolean sobreAcervoExistente) {
    }
}
