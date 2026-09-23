package br.edu.tcc.auditoria.infraestrutura.api;

// Representa o resultado de uma análise em dois blocos: a leitura, que fala de arquivos, e a conferência, que fala de produtos. Separar os dois impede que arquivo ilegível passe por lote limpo.
public record RespostaDaConferencia(
        ReciboDaAnalise leitura,
        ConferenciaExposta conferencia,
        FaixaDeNatureza natureza,
        String aviso) {

    // Valida que a resposta tenha os dois blocos, a faixa de procedência e o aviso de uso.
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
