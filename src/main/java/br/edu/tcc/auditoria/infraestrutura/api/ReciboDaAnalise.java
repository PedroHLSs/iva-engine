package br.edu.tcc.auditoria.infraestrutura.api;

import java.time.Instant;
import java.util.List;

/**
 * O comprovante de uma análise: o que entrou, o que foi lido e o que não foi.
 *
 * <h2>Esta resposta não diz nenhum desfecho, e isso é deliberado</h2>
 *
 * <p>Não há aqui contagem de apontamento, de conforme nem de não avaliado — nem
 * como zero. A razão é que os quatro estados da interface só significam alguma
 * coisa quando aparecem <strong>os quatro juntos</strong>, e apresentá-los pela
 * metade num comprovante de recebimento seria construir exatamente a leitura
 * que esta etapa existe para impedir: "zero apontamentos, então está limpo".</p>
 *
 * <p>A garantia é estrutural, não documental: <strong>não existe neste tipo
 * nenhum campo de desfecho</strong>, então não há número a interpretar errado.
 * Quem quiser o resultado da conferência vai buscá-lo no endereço de
 * {@link #recurso()}.</p>
 *
 * <h2>O que ele diz</h2>
 *
 * <p>Fala só de leitura: quantos documentos e itens entraram, contra qual
 * catálogo e qual conjunto de regras, e quais arquivos não puderam ser lidos.
 * {@code arquivosIlegiveis} é contagem própria, ao lado de tudo, e nunca é
 * somada a nada — arquivo que não pôde ser lido não é nota, é ausência.</p>
 */
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

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" do recibo é obrigatório: sem ele a análise não diz contra o que "
                            + "foi produzida.").formatted(nomeDoCampo));
        }
    }
}
