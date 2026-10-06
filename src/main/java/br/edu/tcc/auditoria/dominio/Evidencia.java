package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;

import java.util.Optional;

// Representa o que a regra olhou e o que encontrou, para uma pessoa conferir o apontamento. Valor encontrado vazio quer dizer que o campo não veio na nota; valor esperado vazio quer dizer que a regra não tinha valor de referência.
public record Evidencia(
        String campoAnalisado,
        Optional<String> valorEncontrado,
        Optional<String> valorEsperado,
        OrigemEvidencia origem) {

    // Valor encontrado do lado da tabela quando a tabela não tem registro para o código que a nota informou. Acrescentado em 04/10/2026 (D025): até essa data R01 e R06 deixavam esse lado vazio, o que pelo contrato acima quer dizer "o campo não veio na nota", e a planilha escrevia "(não informado)" sobre um código que a nota informou.
    public static final String NENHUM_REGISTRO_NA_TABELA = "nenhum registro com este código na tabela carregada";

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
