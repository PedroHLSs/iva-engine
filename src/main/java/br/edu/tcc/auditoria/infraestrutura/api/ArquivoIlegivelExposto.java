package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;

/**
 * Um arquivo que a análise não conseguiu ler, como ele sai na resposta.
 *
 * <p>A {@code origem} já chegou aqui sem o diretório de quem rodou e sem o CNPJ
 * que viaja dentro do nome do arquivo — a limpeza acontece na fronteira da
 * leitura, e tanto o registro de aplicação quanto a coluna do banco a exigem.
 * Esta camada não repete a limpeza: repetir sugeriria que ela pode não ter
 * acontecido antes.</p>
 */
public record ArquivoIlegivelExposto(String origem, String tipoDeErro, String motivo) {

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

    static ArquivoIlegivelExposto de(ArquivoIlegivel ilegivel) {
        return new ArquivoIlegivelExposto(
                ilegivel.origem(), ilegivel.tipoDeErro(), ilegivel.motivo());
    }
}
