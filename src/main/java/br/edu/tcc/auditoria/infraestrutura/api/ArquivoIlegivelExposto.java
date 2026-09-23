package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;

// Representa um arquivo que a análise não conseguiu ler, como sai na resposta. O nome do arquivo já chega aqui sem a pasta e sem o CNPJ; essa limpeza é feita na leitura.
public record ArquivoIlegivelExposto(String origem, String tipoDeErro, String motivo) {

    // Valida que o arquivo tenha origem, tipo de erro e motivo.
    public ArquivoIlegivelExposto {
        if (origem == null || origem.isBlank()) {
            throw new RespostaInvalida(
                    "O arquivo ilegível precisa dizer de qual arquivo veio.");
        }
        if (tipoDeErro == null || tipoDeErro.isBlank()) {
            throw new RespostaInvalida(
                    "O arquivo ilegível precisa dizer que tipo de erro ocorreu.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new RespostaInvalida(
                    "O arquivo ilegível precisa do motivo: sem ele a resposta diz que algo não foi "
                            + "lido sem dizer o que houve.");
        }
    }

    // Método estático que converte o arquivo ilegível da aplicação para a resposta.
    static ArquivoIlegivelExposto de(ArquivoIlegivel ilegivel) {
        return new ArquivoIlegivelExposto(
                ilegivel.origem(), ilegivel.tipoDeErro(), ilegivel.motivo());
    }
}
