package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.AchadoInvalido;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Apontamento de incoerência: o produto do sistema.
 *
 * <p>Um apontamento diz qual regra o gerou e em que versão, sobre qual
 * documento e item, com que evidências, sob qual fundamento e em qual vigência.
 * Sem esse conjunto o apontamento não é conferível, e um apontamento não
 * conferível não serve para auditoria.</p>
 *
 * <p>{@code numeroItem} é {@link OptionalInt} porque há incoerência de
 * documento — entre a UF do emitente e a do destinatário, por exemplo — que não
 * pertence a nenhum item.</p>
 *
 * <p><strong>{@code fundamentoNormativo} é obrigatório e o domínio não fornece
 * nenhum valor padrão.</strong> Quem cria o apontamento informa o fundamento;
 * não há texto de legislação escrito em código neste projeto. O mesmo vale para
 * {@code vigenciaAplicada}, cujas datas vêm das tabelas importadas.</p>
 *
 * @param regraId             identificação da regra que gerou o apontamento
 * @param regraVersao         versão da regra, para que o relatório seja reproduzível
 * @param severidade          gravidade atribuída
 * @param chaveAcesso         documento apontado
 * @param numeroItem          item apontado, vazio se o apontamento é do documento
 * @param evidencias          o que foi olhado e o que se encontrou; ao menos uma
 * @param fundamentoNormativo base normativa informada por quem definiu a regra
 * @param vigenciaAplicada    período de vigência considerado na avaliação
 * @param valorEmRisco        montante, ou o motivo de não haver montante
 */
public record Achado(
        String regraId,
        String regraVersao,
        Severidade severidade,
        ChaveAcesso chaveAcesso,
        OptionalInt numeroItem,
        List<Evidencia> evidencias,
        String fundamentoNormativo,
        PeriodoVigencia vigenciaAplicada,
        ValorEmRisco valorEmRisco) {

    public Achado {
        exigirTexto(regraId, "regraId");
        exigirTexto(regraVersao, "regraVersao");
        exigirPresente(severidade, "severidade");
        exigirPresente(chaveAcesso, "chaveAcesso");
        exigirTexto(fundamentoNormativo, "fundamentoNormativo");
        exigirPresente(vigenciaAplicada, "vigenciaAplicada");
        exigirPresente(valorEmRisco, "valorEmRisco");

        if (numeroItem == null) {
            throw new AchadoInvalido(
                    "Apontamento de documento, sem item, se representa com OptionalInt.empty(), nunca com nulo.");
        }
        if (numeroItem.isPresent() && numeroItem.getAsInt() < 1) {
            throw new AchadoInvalido(
                    "O número do item apontado deve ser maior ou igual a 1, mas veio %d."
                            .formatted(numeroItem.getAsInt()));
        }

        if (evidencias == null) {
            throw new AchadoInvalido("A lista de evidências não pode ser nula.");
        }
        if (evidencias.isEmpty()) {
            throw new AchadoInvalido(
                    "Um apontamento precisa de ao menos uma evidência: sem evidência não há o que conferir.");
        }
        // Varredura por stream, e não List.contains(null): lista imutável lança
        // NullPointerException ao ser consultada com nulo.
        if (evidencias.stream().anyMatch(Objects::isNull)) {
            throw new AchadoInvalido("A lista de evidências não pode conter elemento nulo.");
        }
        evidencias = List.copyOf(evidencias);
    }

    /** Indica se o apontamento é sobre um item específico, e não sobre o documento inteiro. */
    public boolean ehDeItem() {
        return numeroItem.isPresent();
    }

    /**
     * Montante do apontamento, vazio quando não calculável.
     *
     * <p>Atalho para {@code valorEmRisco().valor()}. Quando vazio, o motivo
     * está em {@code valorEmRisco().motivoDaAusencia()}.</p>
     */
    public Optional<BigDecimal> quantiaEmRisco() {
        return valorEmRisco.valor();
    }

    private static void exigirPresente(Object valor, String nomeDoCampo) {
        if (valor == null) {
            throw new AchadoInvalido("O campo \"%s\" do apontamento é obrigatório.".formatted(nomeDoCampo));
        }
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AchadoInvalido("O campo \"%s\" do apontamento é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
