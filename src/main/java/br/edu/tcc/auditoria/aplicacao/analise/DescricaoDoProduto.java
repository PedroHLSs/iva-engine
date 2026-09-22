package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.Optional;

/**
 * A descrição que o emitente deu ao produto, ou o motivo de não haver uma.
 *
 * <h2>Por que ela não mora em {@code ItemDocumento}</h2>
 *
 * <p>Porque nenhuma regra a examina. {@code ItemDocumento} é o que o motor
 * audita, e a descrição não entra em avaliação nenhuma — ela serve a quem lê a
 * tela, para comparar o que o emitente escreveu com o que o catálogo descreve
 * para aquele NCM. Divergência entre as duas é sinal de classificação errada, e
 * é leitura de pessoa, não de regra.</p>
 *
 * <h2>Três estados, não dois</h2>
 *
 * <p>{@link Declarada} é o texto do documento. {@link NaoDeclarada} carrega
 * <em>qual</em> ausência é: o documento não trouxe o campo, ou a análise é
 * anterior ao registro de descrições. São coisas diferentes — a primeira fala do
 * documento, a segunda fala do sistema —, e colapsá-las faria a tela dizer que o
 * emitente não descreveu o produto quando o que houve foi o sistema não ter
 * guardado.</p>
 *
 * <p>É a mesma forma de {@code ValorEmRisco} e de {@code VersaoDaRegra}: tipo
 * selado, ausência com motivo, nenhum valor padrão.</p>
 */
public sealed interface DescricaoDoProduto {

    /** O documento não trouxe o campo. */
    String NAO_VEIO_NO_DOCUMENTO = "o documento não declarou descrição para este item";

    /** A leitura consultada não registrou entrada para este item. */
    String NAO_LIDA_NESTA_ANALISE =
            "este item não foi registrado pela leitura desta análise. É incoerência interna, e não "
                    + "afirmação sobre o que o documento trouxe";

    /** A análise rodou antes de o sistema passar a guardar descrições. */
    String ANTERIOR_AO_REGISTRO =
            "esta análise é anterior ao registro da descrição do produto, e o sistema guarda o item, "
                    + "não versões dele: não há como recuperar o que ela leu";

    /** O texto, quando há. */
    Optional<String> texto();

    /** O motivo de não haver texto, quando não há. */
    Optional<String> motivoDaAusencia();

    /** O que o emitente escreveu, já saneado de identificador em texto claro. */
    record Declarada(String valor) implements DescricaoDoProduto {

        public Declarada {
            if (valor == null || valor.isBlank()) {
                throw new AnaliseInvalida(
                        "Descrição em branco é ausência, e ausência se representa com NaoDeclarada e o "
                                + "motivo. Texto vazio na tela seria indistinguível de campo que "
                                + "ninguém preencheu.");
            }
        }

        @Override
        public Optional<String> texto() {
            return Optional.of(valor);
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.empty();
        }
    }

    /** Não há descrição, e este é o motivo. */
    record NaoDeclarada(String motivo) implements DescricaoDoProduto {

        public NaoDeclarada {
            if (motivo == null || motivo.isBlank()) {
                throw new AnaliseInvalida(
                        "Ausência de descrição precisa do motivo: sem ele a tela mostra um espaço em "
                                + "branco, e espaço em branco não diz de quem é a falta.");
            }
        }

        @Override
        public Optional<String> texto() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.of(motivo);
        }
    }

    /** Declarada quando o texto veio; não declarada, com o motivo, quando não. */
    static DescricaoDoProduto de(Optional<String> texto) {
        if (texto == null) {
            throw new AnaliseInvalida(
                    "Ausência de descrição se representa com Optional.empty(), nunca com nulo.");
        }
        return texto.filter(lido -> !lido.isBlank())
                .<DescricaoDoProduto>map(Declarada::new)
                .orElseGet(() -> new NaoDeclarada(NAO_VEIO_NO_DOCUMENTO));
    }

    /** A ausência das linhas gravadas antes desta etapa. */
    static DescricaoDoProduto anteriorAoRegistro() {
        return new NaoDeclarada(ANTERIOR_AO_REGISTRO);
    }

    /**
     * O item não passou pela leitura que está sendo consultada.
     *
     * <p>Não deveria acontecer: a leitura registra uma entrada por item que
     * constrói. Se acontecer, é incoerência interna, e a tela diz isso em vez de
     * afirmar que o emitente não descreveu o produto — que seria pôr no documento
     * uma falta que é do sistema.</p>
     */
    static DescricaoDoProduto naoLidaNestaAnalise() {
        return new NaoDeclarada(NAO_LIDA_NESTA_ANALISE);
    }
}
