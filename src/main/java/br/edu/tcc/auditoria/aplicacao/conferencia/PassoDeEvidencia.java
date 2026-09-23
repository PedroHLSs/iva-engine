package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;

import java.util.Optional;

// Representa uma evidência do apontamento no formato da tela; valor encontrado vazio e valor esperado vazio querem dizer coisas diferentes.
public record PassoDeEvidencia(
        String campoAnalisado,
        Optional<String> valorEncontrado,
        Optional<String> valorEsperado,
        String origem) {

    // Texto que explica o valor encontrado vazio.
    public static final String NAO_VEIO_NO_DOCUMENTO = "o documento não trouxe este campo";

    // Texto que explica o valor esperado vazio.
    public static final String SEM_REFERENCIA_A_OPOR =
            "a regra não tinha valor de referência a opor: ela conferiu campos do próprio documento "
                    + "entre si";

    // Valida que a evidência tenha campo examinado, valores em Optional e origem.
    public PassoDeEvidencia {
        if (campoAnalisado == null || campoAnalisado.isBlank()) {
            throw new ConferenciaInvalida("A evidência precisa dizer qual campo foi examinado.");
        }
        if (valorEncontrado == null || valorEsperado == null) {
            throw new ConferenciaInvalida(
                    "Ausência de valor na evidência se representa com Optional.empty(), nunca com nulo.");
        }
        if (origem == null || origem.isBlank()) {
            throw new ConferenciaInvalida(
                    "A evidência precisa dizer de onde o valor saiu: é o que responde à pergunta \"por "
                            + "que você diz que o esperado era isso?\".");
        }
    }

    // Método estático que cria o passo de exibição a partir da evidência do domínio.
    public static PassoDeEvidencia de(Evidencia evidencia) {
        if (evidencia == null) {
            throw new ConferenciaInvalida("Não há evidência a apresentar.");
        }
        return new PassoDeEvidencia(
                evidencia.campoAnalisado(),
                evidencia.valorEncontrado(),
                evidencia.valorEsperado(),
                origemDe(evidencia.origem()));
    }

    // Método auxiliar que traduz a origem da evidência em texto, com switch exaustivo e sem default.
    private static String origemDe(OrigemEvidencia origem) {
        return switch (origem) {
            case OrigemEvidencia.DoDocumento doDocumento ->
                    "lido do documento, em %s".formatted(doDocumento.localizacao());
            case OrigemEvidencia.DeTabelaNormativa daTabela ->
                    "lido da tabela %s, versão %s"
                            .formatted(daTabela.nomeTabela(), daTabela.versaoTabela());
            case OrigemEvidencia.DaRegra daRegra ->
                    "derivado pela própria regra: %s".formatted(daRegra.descricao());
        };
    }
}
