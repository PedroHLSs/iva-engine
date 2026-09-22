package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * O resultado de uma análise: o que foi lido, e o que a conferência concluiu.
 *
 * <p>Dois blocos nomeados, e não um punhado de campos irmãos. A separação é o
 * ponto: {@code leitura} fala de arquivos — quantos entraram, quantos não
 * puderam ser abertos — e {@code conferencia} fala de produtos. Misturar os dois
 * é como um lote com três arquivos ilegíveis vira "lote quase limpo".</p>
 *
 * <p>{@code aviso} acompanha toda resposta de resultado, e vem do servidor para
 * que nenhuma tela precise lembrar de escrevê-lo — ver {@link AvisoDeUso}.</p>
 */
public record RespostaDaConferencia(
        ReciboDaAnalise leitura,
        ConferenciaExposta conferencia,
        FaixaDeNatureza natureza,
        String aviso) {

    public RespostaDaConferencia {
        if (leitura == null) {
            throw new RespostaInvalida("A resposta precisa do bloco de leitura.");
        }
        if (conferencia == null) {
            throw new RespostaInvalida(
                    "A resposta precisa do bloco de conferência, ainda que a análise não tenha lido "
                            + "produto nenhum: quatro zeros são uma afirmação, e a ausência do bloco "
                            + "não é.");
        }
        if (natureza == null) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com a faixa de procedência. Dado de demonstração "
                            + "sem aviso é afirmação falsa sobre a lei.");
        }
        if (aviso == null || aviso.isBlank()) {
            throw new RespostaInvalida(
                    "Toda resposta de resultado sai com o aviso de uso. Ele não é opcional por tela.");
        }
    }
}
