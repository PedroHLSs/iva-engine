package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.RotuloEsperadoInvalido;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * O que uma pessoa afirmou, item a item, que o motor deveria concluir.
 *
 * <p>São dois, e só dois. O gabarito é a verdade de referência contra a qual o
 * sistema é medido, e verdade de referência não tem terceiro estado.</p>
 *
 * <h2>Por que {@code NAO_AVALIADO} não é rótulo</h2>
 *
 * <p>{@code NAO_AVALIADO} é desfecho do sistema, não fato sobre o documento.
 * Quem rotula olha o item e responde "aqui há incoerência" ou "aqui não há";
 * responder "aqui o sistema não vai conseguir julgar" seria rotular o sistema, e
 * não o documento — e mediria a ferramenta contra a expectativa que já se tem
 * dela, que é exatamente o erro que este harness existe para não cometer. Quando
 * o motor não conclui, isso aparece na cobertura, e nunca em precisão ou
 * recall.</p>
 */
public enum RotuloEsperado {

    /** A pessoa que rotulou afirma que há incoerência neste item, para esta regra. */
    ACHADO,

    /** A pessoa que rotulou afirma que não há incoerência neste item, para esta regra. */
    CONFORME;

    /**
     * Converte o texto lido do gabarito, recusando qualquer outra coisa.
     *
     * <p>Não normaliza acento, não aceita abreviação e não adivinha: um gabarito
     * com "conf" numa linha e "CONFORME" na outra é um gabarito que ninguém
     * conferiu, e aceitá-lo em silêncio esconderia isso.</p>
     */
    public static RotuloEsperado de(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new RotuloEsperadoInvalido(
                    "O rótulo esperado é obrigatório. Aceitos: %s.".formatted(aceitos()));
        }
        String limpo = texto.strip().toUpperCase();
        return Arrays.stream(values())
                .filter(rotulo -> rotulo.name().equals(limpo))
                .findFirst()
                .orElseThrow(() -> new RotuloEsperadoInvalido(recusa(texto.strip())));
    }

    private static String recusa(String informado) {
        String explicacao = "NAO_AVALIADO".equals(informado.toUpperCase())
                ? " \"NAO_AVALIADO\" é desfecho do sistema, não fato sobre o documento: o gabarito diz "
                        + "se há ou não incoerência, e a incapacidade de julgar aparece na cobertura."
                : "";
        return "Rótulo esperado desconhecido: \"%s\". Aceitos: %s.%s"
                .formatted(informado, aceitos(), explicacao);
    }

    private static String aceitos() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
