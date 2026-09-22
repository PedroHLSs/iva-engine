package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

/**
 * O que a carga diz sobre <strong>um</strong> tributo, na data do documento.
 *
 * <p>Um por {@link Tributo}, sempre, inclusive os que a carga não alcança — que
 * aparecem com o motivo em vez de sumirem da tela. Tributo que some é lido como
 * tributo que não incide.</p>
 *
 * <h2>Os rótulos são nomes de eixo, não texto normativo</h2>
 *
 * <p>{@code rotulo} e {@code familia} são as mesmas palavras que o enum do
 * domínio já usa no próprio Javadoc para dizer o que cada constante é. Nenhuma
 * delas afirma percentual, incidência, base ou hierarquia entre tributos: isso
 * está na carga, e é o que os campos de alíquota trazem.</p>
 *
 * <p>O {@code switch} é exaustivo e sem {@code default}, de propósito. Um tributo
 * novo no domínio quebra a compilação aqui, e quebrar é o certo: ele precisa
 * ganhar rótulo e bloco antes de aparecer numa tela.</p>
 */
public record TratamentoDeTributo(
        Tributo tributo,
        String rotulo,
        String familia,
        LeituraDoCatalogo<AliquotaDoCatalogo> aliquotas) {

    public TratamentoDeTributo {
        if (tributo == null) {
            throw new ConferenciaInvalida("O tratamento precisa dizer de que tributo ele é.");
        }
        if (rotulo == null || rotulo.isBlank() || familia == null || familia.isBlank()) {
            throw new ConferenciaInvalida(
                    "O tratamento de %s precisa de rótulo e de bloco.".formatted(tributo));
        }
        if (aliquotas == null) {
            throw new ConferenciaInvalida(
                    "O tratamento de %s precisa da leitura do catálogo, ainda que ela seja o motivo de "
                            + "não haver alíquota.".formatted(tributo));
        }
    }

    public static TratamentoDeTributo de(
            Tributo tributo, LeituraDoCatalogo<AliquotaDoCatalogo> aliquotas) {

        if (tributo == null) {
            throw new ConferenciaInvalida("Não há tributo a apresentar.");
        }
        return new TratamentoDeTributo(tributo, rotuloDe(tributo), familiaDe(tributo), aliquotas);
    }

    private static String rotuloDe(Tributo tributo) {
        return switch (tributo) {
            case IBS_UF -> "IBS - parcela estadual";
            case IBS_MUN -> "IBS - parcela municipal";
            case CBS -> "CBS";
        };
    }

    private static String familiaDe(Tributo tributo) {
        return switch (tributo) {
            case IBS_UF, IBS_MUN -> "IBS";
            case CBS -> "CBS";
        };
    }
}
