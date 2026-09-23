package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;

import java.util.Optional;

// Representa o que a regra olhou e o que encontrou, para uma pessoa conferir o apontamento. Valor encontrado vazio quer dizer que o campo não veio na nota; valor esperado vazio quer dizer que a regra não tinha valor de referência.
public record Evidencia(
        String campoAnalisado,
        Optional<String> valorEncontrado,
        Optional<String> valorEsperado,
        OrigemEvidencia origem) {

    // Valida que a evidência tenha o campo analisado, os dois valores em Optional e a origem.
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
