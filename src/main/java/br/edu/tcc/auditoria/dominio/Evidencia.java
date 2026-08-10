package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;

import java.util.Optional;

/**
 * Registro do que a regra olhou e do que encontrou, para que o apontamento
 * possa ser conferido por uma pessoa.
 *
 * <p>Os dois valores são {@code Optional} por razões diferentes e igualmente
 * necessárias:</p>
 *
 * <ul>
 *   <li>{@code valorEncontrado} vazio significa que o campo não veio no
 *       documento — que costuma ser justamente o motivo do apontamento. Um
 *       campo ausente não é registrado como {@code "0"} nem como {@code ""}.</li>
 *   <li>{@code valorEsperado} vazio significa que a regra não tem um valor de
 *       referência a opor, apenas constatou incoerência interna.</li>
 * </ul>
 *
 * <p>Texto vazio dentro do {@code Optional} é permitido: um campo que veio no
 * XML com conteúdo em branco é informação, e diferente de campo ausente.</p>
 *
 * @param campoAnalisado  qual campo foi examinado
 * @param valorEncontrado o que o documento declarou, se declarou
 * @param valorEsperado   o que a referência indicava, se há referência
 * @param origem          de onde saiu o valor esperado ou o encontrado
 */
public record Evidencia(
        String campoAnalisado,
        Optional<String> valorEncontrado,
        Optional<String> valorEsperado,
        OrigemEvidencia origem) {

    public Evidencia {
        if (campoAnalisado == null || campoAnalisado.isBlank()) {
            throw new EvidenciaInvalida("A evidência precisa dizer qual campo foi analisado.");
        }
        if (valorEncontrado == null) {
            throw new EvidenciaInvalida(
                    "Campo não informado no documento se representa com Optional.empty() "
                            + "em valorEncontrado, nunca com nulo.");
        }
        if (valorEsperado == null) {
            throw new EvidenciaInvalida(
                    "Ausência de valor de referência se representa com Optional.empty() "
                            + "em valorEsperado, nunca com nulo.");
        }
        if (origem == null) {
            throw new EvidenciaInvalida(
                    "A evidência precisa registrar sua origem para ser rastreável.");
        }
    }
}
