package br.edu.tcc.auditoria.dominio.acuracia;

import br.edu.tcc.auditoria.dominio.excecao.RotuloEsperadoInvalido;

import java.util.Arrays;
import java.util.stream.Collectors;

// Enum com os dois rótulos que o gabarito aceita; NAO_AVALIADO não é rótulo, porque é desfecho do sistema e não fato sobre o documento.
public enum RotuloEsperado {

    // A pessoa que rotulou afirma que há incoerência neste item, para esta regra.
    ACHADO,

    // A pessoa que rotulou afirma que não há incoerência neste item, para esta regra.
    CONFORME;

    // Método estático que converte o texto lido do gabarito, recusando qualquer valor fora dos dois rótulos.
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

    // Método auxiliar que monta a mensagem de recusa, com explicação à parte quando o texto é NAO_AVALIADO.
    private static String recusa(String informado) {
        String explicacao = "NAO_AVALIADO".equals(informado.toUpperCase())
                ? " \"NAO_AVALIADO\" é desfecho do sistema, não fato sobre o documento: o gabarito diz "
                        + "se há ou não incoerência, e a incapacidade de julgar aparece na cobertura."
                : "";
        return "Rótulo esperado desconhecido: \"%s\". Aceitos: %s.%s"
                .formatted(informado, aceitos(), explicacao);
    }

    // Método auxiliar que lista os rótulos aceitos, para as mensagens de erro.
    private static String aceitos() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
