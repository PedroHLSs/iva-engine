package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.List;

// Representa o comprovante de uma análise: quantos documentos e itens entraram, contra qual catálogo e quais regras, e quais arquivos não foram lidos. De propósito, não tem nenhum campo de resultado; o resultado da conferência fica no endereço de recurso().
public record ReciboDaAnalise(
        String id,
        String recurso,
        Instant dataHora,
        String versaoCatalogo,
        String versaoConjuntoRegras,
        int documentosLidos,
        int itensLidos,
        int arquivosIlegiveis,
        List<ArquivoIlegivelExposto> arquivosQueNaoForamLidos,
        String comoFoiALeitura) {

    // Valida o recibo: exige identificador, endereço, data e hora, versões, contagens não negativas, a lista de ilegíveis batendo com a contagem e o texto de como foi a leitura.
    public ReciboDaAnalise {
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
        if (documentosLidos < 0 || itensLidos < 0 || arquivosIlegiveis < 0) {
            throw new RespostaInvalida("Contagem de leitura não pode ser negativa.");
        }
        if (arquivosQueNaoForamLidos == null) {
            throw new RespostaInvalida(
                    "A lista de arquivos não lidos deve ser vazia quando todos foram lidos, nunca nula.");
        }
        if (arquivosQueNaoForamLidos.size() != arquivosIlegiveis) {
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
        arquivosQueNaoForamLidos = List.copyOf(arquivosQueNaoForamLidos);
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
