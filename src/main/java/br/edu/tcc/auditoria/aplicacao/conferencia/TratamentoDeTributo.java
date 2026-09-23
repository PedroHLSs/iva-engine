package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.catalogo.Tributo;

// Representa o que a carga diz sobre um tributo na data do documento; os três tributos aparecem sempre, inclusive os sem alíquota.
public record TratamentoDeTributo(
        Tributo tributo,
        String rotulo,
        String familia,
        LeituraDoCatalogo<AliquotaDoCatalogo> aliquotas) {

    // Valida que o tratamento tenha tributo, rótulo, bloco e leitura do catálogo.
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

    // Método estático que cria o tratamento do tributo com o rótulo e o bloco correspondentes.
    public static TratamentoDeTributo de(
            Tributo tributo, LeituraDoCatalogo<AliquotaDoCatalogo> aliquotas) {

        if (tributo == null) {
            throw new ConferenciaInvalida("Não há tributo a apresentar.");
        }
        return new TratamentoDeTributo(tributo, rotuloDe(tributo), familiaDe(tributo), aliquotas);
    }

    // Método auxiliar que retorna o nome do tributo exibido na tela.
    private static String rotuloDe(Tributo tributo) {
        return switch (tributo) {
            case IBS_UF -> "IBS - parcela estadual";
            case IBS_MUN -> "IBS - parcela municipal";
            case CBS -> "CBS";
        };
    }

    // Método auxiliar que retorna o bloco, IBS ou CBS, a que o tributo pertence.
    private static String familiaDe(Tributo tributo) {
        return switch (tributo) {
            case IBS_UF, IBS_MUN -> "IBS";
            case CBS -> "CBS";
        };
    }
}
