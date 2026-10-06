package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.List;

// Representa o comprovante de uma análise: quantos documentos e itens entraram, contra qual catálogo e quais regras, e quais arquivos não foram lidos. De propósito, não tem nenhum campo de resultado; o resultado da conferência fica no endereço de recurso().
// Emenda de 04/10/2026 (D018): quando a leitura da execução não foi registrada, arquivosIlegiveis e a lista vêm null, com motivoDosArquivosIlegiveisAusentes dizendo por quê — o par da D009. Até essa data a contagem era int e saía 0 nesse caso, o que afirmava que nenhum arquivo tinha falhado.
public record ReciboDaAnalise(
        String id,
        String recurso,
        Instant dataHora,
        String versaoCatalogo,
        String versaoConjuntoRegras,
        int documentosLidos,
        int itensLidos,
        Integer arquivosIlegiveis,
        List<ArquivoIlegivelExposto> arquivosQueNaoForamLidos,
        String motivoDosArquivosIlegiveisAusentes,
        Integer documentosRepetidosDescartados,
        String motivoDosRepetidosAusentes,
        String comoFoiALeitura,
        ToleranciaExposta toleranciaDeValor) {

    // Emenda de 04/10/2026 (D023): traz a tolerância de valor da R05 que a execução usou, com a origem.

    // Emenda de 04/10/2026 (D019): documentosRepetidosDescartados diz quantas cópias idênticas de um documento já lido o lote descartou; null, com o motivo ao lado, quando a execução não registrou essa contagem.

    // Valida o recibo: exige identificador, endereço, data e hora, versões, contagens não negativas, a lista de ilegíveis batendo com a contagem — ou as duas ausentes com o motivo — e o texto de como foi a leitura.
    public ReciboDaAnalise {
        if (toleranciaDeValor == null) {
            throw new RespostaInvalida(
                    "A resposta traz a tolerância de valor da R05 que a execução usou, ou o motivo de não haver "
                            + "(D023): duas execuções com tolerâncias diferentes dão resultados diferentes.");
        }
        if (id == null || id.isBlank()) {
            throw new RespostaInvalida("O recibo da análise precisa do identificador.");
        }
        if (recurso == null || recurso.isBlank()) {
            throw new RespostaInvalida(
                    "O recibo precisa trazer o endereço da própria análise: é por ele que se chega "
                            + "ao resultado da conferência, que não está aqui.");
        }
        if (dataHora == null) {
            throw new RespostaInvalida("O recibo da análise precisa registrar quando ela rodou.");
        }
        exigirTexto(versaoCatalogo, "versaoCatalogo");
        exigirTexto(versaoConjuntoRegras, "versaoConjuntoRegras");
        if (documentosLidos < 0 || itensLidos < 0 || (arquivosIlegiveis != null && arquivosIlegiveis < 0)) {
            throw new RespostaInvalida("Contagem de leitura não pode ser negativa.");
        }
        if (arquivosIlegiveis == null || arquivosQueNaoForamLidos == null) {
            if (arquivosIlegiveis != null || arquivosQueNaoForamLidos != null) {
                throw new RespostaInvalida(
                        "A contagem e a lista de arquivos não lidos são ausentes juntas ou presentes "
                                + "juntas: uma sem a outra é resposta contraditória.");
            }
            if (motivoDosArquivosIlegiveisAusentes == null || motivoDosArquivosIlegiveisAusentes.isBlank()) {
                throw new RespostaInvalida(
                        "Sem a contagem de arquivos não lidos, o recibo precisa dizer por quê. Campo "
                                + "nulo sem motivo é indistinguível de campo esquecido.");
            }
        } else if (motivoDosArquivosIlegiveisAusentes != null) {
            throw new RespostaInvalida(
                    "Com a contagem de arquivos não lidos presente, não há motivo de ausência a dar.");
        }
        if (documentosRepetidosDescartados == null) {
            if (motivoDosRepetidosAusentes == null || motivoDosRepetidosAusentes.isBlank()) {
                throw new RespostaInvalida(
                        "Sem a contagem de documentos repetidos, o recibo precisa dizer por quê.");
            }
        } else if (documentosRepetidosDescartados < 0) {
            throw new RespostaInvalida("Contagem de documentos repetidos não pode ser negativa.");
        } else if (motivoDosRepetidosAusentes != null) {
            throw new RespostaInvalida(
                    "Com a contagem de documentos repetidos presente, não há motivo de ausência a dar.");
        }
        if (arquivosQueNaoForamLidos != null && arquivosQueNaoForamLidos.size() != arquivosIlegiveis) {
            throw new RespostaInvalida(
                    ("A contagem diz %d arquivo(s) não lido(s) e a lista traz %d. Uma resposta "
                            + "internamente contraditória é pior que nenhuma.")
                            .formatted(arquivosIlegiveis, arquivosQueNaoForamLidos.size()));
        }
        if (comoFoiALeitura == null || comoFoiALeitura.isBlank()) {
            throw new RespostaInvalida(
                    "O recibo precisa dizer por extenso como foi a leitura. Campo em branco é "
                            + "indistinguível de campo que ninguém preencheu.");
        }
        arquivosQueNaoForamLidos = arquivosQueNaoForamLidos == null ? null : List.copyOf(arquivosQueNaoForamLidos);
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" do recibo é obrigatório: sem ele a análise não diz contra o que "
                            + "foi produzida.").formatted(nomeDoCampo));
        }
    }
}
