package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.regex.Pattern;

/**
 * Um arquivo que a análise não conseguiu ler.
 *
 * <p>Não é nota sem divergência e não é nota com divergência: é ausência. Anda
 * ao lado dos quatro estados da interface, contado à parte, e não existe nenhum
 * campo a que ele possa ser somado por descuido.</p>
 *
 * <h2>A origem não pode trazer chave de acesso</h2>
 *
 * <p>Arquivo de NF-e costuma se chamar pelo número da chave, e os dígitos
 * intermediários dela carregam o CNPJ do emitente (D005). Quem constrói este
 * registro já entrega a origem sem o identificador — a substituição pelo
 * pseudônimo acontece na infraestrutura, que é a única camada que vê o nome
 * original.</p>
 *
 * <p>A recusa aqui é a primeira das duas barreiras; a segunda é a restrição de
 * formato da coluna, no banco. Nenhuma das duas cita a origem na mensagem: citá-la
 * escreveria no log exatamente o que elas existem para não deixar entrar.</p>
 */
public record ArquivoIlegivel(String origem, String tipoDeErro, String motivo) {

    /** Qualquer corrida de 44 dígitos: é a forma de uma chave de acesso. */
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

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new AnaliseInvalida(
                    ("O campo \"%s\" do arquivo ilegível é obrigatório: uma falha sem ele diz que algo "
                            + "não foi lido sem dizer o quê.").formatted(nomeDoCampo));
        }
    }
}
