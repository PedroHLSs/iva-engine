package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.regex.Pattern;

// Representa um arquivo com falta de informações para se tornar legível
public record ArquivoIlegivel(String origem, String tipoDeErro, String motivo) {

    private static final Pattern FORMA_DE_CHAVE = Pattern.compile("[0-9]{44}");

    public ArquivoIlegivel {
        exigirTexto(origem, "origem");
        exigirTexto(tipoDeErro, "tipoDeErro");
        exigirTexto(motivo, "motivo");
        if (FORMA_DE_CHAVE.matcher(origem).find()) {
            throw new AnaliseInvalida(
                    "A origem do arquivo ilegível traz uma corrida de 44 dígitos, que é a forma de uma "
                            + "chave de acesso — e os dígitos do meio dela são o CNPJ do emitente. "
                            + "Substitua a chave pelo pseudônimo antes de registrar a falha. O valor "
                            + "recusado não é repetido nesta mensagem, de propósito.");
        }
    }
    // Valida se o valor do campo é nulo ou vazio, lançando uma exceção se for o caso.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AnaliseInvalida(
                    ("O campo \"%s\" do arquivo ilegível é obrigatório: uma falha sem ele diz que algo "
                            + "não foi lido sem dizer o quê.").formatted(nomeDoCampo));
        }
    }
}
