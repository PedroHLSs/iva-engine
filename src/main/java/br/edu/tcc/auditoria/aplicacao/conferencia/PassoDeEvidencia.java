package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;

import java.util.Optional;

/**
 * Uma evidência do apontamento, no formato em que a tela a lê.
 *
 * <h2>Os dois vazios querem dizer coisas diferentes</h2>
 *
 * <p>É a distinção que {@code Evidencia} já faz no domínio, e que se perderia se
 * a tela escrevesse um traço nos dois casos. {@code valorEncontrado} vazio é o
 * campo que não veio no documento — costuma ser <em>o motivo</em> do
 * apontamento. {@code valorEsperado} vazio é a regra não ter referência a opor:
 * ela constatou incoerência interna, entre campos do próprio documento.</p>
 *
 * <p>Por isso os dois textos de ausência são constantes daqui e não frases que a
 * interface inventa. São diferentes de propósito.</p>
 *
 * <h2>A origem é traduzida por switch exaustivo</h2>
 *
 * <p>{@code OrigemEvidencia} é selada e tem três variantes. O {@code switch} de
 * {@link #origemDe} não tem {@code default}: uma origem nova quebra a compilação
 * aqui, em vez de aparecer na tela como texto vazio.</p>
 */
public record PassoDeEvidencia(
        String campoAnalisado,
        Optional<String> valorEncontrado,
        Optional<String> valorEsperado,
        String origem) {

    /** O que significa {@code valorEncontrado} vazio. */
    public static final String NAO_VEIO_NO_DOCUMENTO = "o documento não trouxe este campo";

    /** O que significa {@code valorEsperado} vazio. */
    public static final String SEM_REFERENCIA_A_OPOR =
            "a regra não tinha valor de referência a opor: ela conferiu campos do próprio documento "
                    + "entre si";

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
