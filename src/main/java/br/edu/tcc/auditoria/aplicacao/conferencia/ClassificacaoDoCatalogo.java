package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * O que a carga diz sobre o {@code cClassTrib} declarado, na data do documento.
 *
 * <p>É a fundamentação da tela de detalhe: dispositivo citado pela fonte, CSTs
 * que aquele código admite, se a carga o marca como benefício, a redução quando
 * há, e os campos que passam a ser exigidos. Todos são conteúdo importado.</p>
 *
 * <h2>Redução ausente não é redução zero</h2>
 *
 * <p>{@code percentualReducao} continua {@link Optional} até a tela, pelo mesmo
 * motivo pelo qual o é no catálogo: uma carga que não declara redução e uma que
 * declara redução de zero afirmam coisas diferentes, e transformar a primeira na
 * segunda aqui seria inventar um percentual — proibido neste projeto mesmo
 * quando o número inventado é zero.</p>
 */
public record ClassificacaoDoCatalogo(
        String codigo,
        List<String> cstsAdmitidos,
        String dispositivoLegal,
        boolean indicadorDeBeneficio,
        Optional<BigDecimal> percentualReducao,
        List<String> camposObrigatoriosCondicionados,
        ReferenciaNormativa referencia) {

    public ClassificacaoDoCatalogo {
        if (codigo == null || codigo.isBlank()) {
            throw new ConferenciaInvalida("A classificação precisa do código a que se refere.");
        }
        if (dispositivoLegal == null || dispositivoLegal.isBlank()) {
            throw new ConferenciaInvalida(
                    "A classificação do catálogo sempre cita dispositivo; vazio aqui seria dado perdido "
                            + "no caminho.");
        }
        if (cstsAdmitidos == null || camposObrigatoriosCondicionados == null) {
            throw new ConferenciaInvalida(
                    "As listas de CST e de campos condicionados devem ser vazias quando não há nenhum, "
                            + "nunca nulas.");
        }
        if (percentualReducao == null) {
            throw new ConferenciaInvalida(
                    "Redução não declarada se representa com Optional.empty(), nunca com nulo e nunca "
                            + "com zero.");
        }
        if (referencia == null) {
            throw new ConferenciaInvalida("A classificação precisa da vigência e da fonte.");
        }
        cstsAdmitidos = List.copyOf(cstsAdmitidos);
        camposObrigatoriosCondicionados = List.copyOf(camposObrigatoriosCondicionados);
    }

    public static ClassificacaoDoCatalogo de(ClassificacaoTributaria registro) {
        if (registro == null) {
            throw new ConferenciaInvalida("Não há classificação tributária a apresentar.");
        }
        return new ClassificacaoDoCatalogo(
                registro.codigo().valor(),
                // O conjunto do catálogo não tem ordem; a tela precisa de uma
                // estável, ou duas aberturas da mesma nota listariam diferente.
                registro.cstsCompativeis().stream()
                        .map(CodigoCst::valor)
                        .sorted(Comparator.naturalOrder())
                        .toList(),
                registro.dispositivoLegal(),
                registro.indicadorDeBeneficio(),
                registro.percentualReducao(),
                registro.camposObrigatoriosCondicionados(),
                ReferenciaNormativa.de(registro.procedencia()));
    }

    /** Se a carga admite este CST junto deste código, na vigência apresentada. */
    public boolean admite(String cst) {
        return cst != null && cstsAdmitidos.contains(cst);
    }
}
