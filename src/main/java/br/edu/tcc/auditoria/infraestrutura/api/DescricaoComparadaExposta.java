package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.conferencia.DescricaoDeNcm;
import br.edu.tcc.auditoria.aplicacao.conferencia.LeituraDoCatalogo;

/**
 * A descrição que o emitente escreveu, ao lado da que o catálogo dá ao NCM.
 *
 * <h2>As duas juntas dizem o que nenhuma diz sozinha</h2>
 *
 * <p>Se a nota descreve "TUBO PVC SOLDAVEL 50MM" e o catálogo descreve outra
 * coisa para o NCM declarado, o sinal é de classificação errada. Não é
 * apontamento de regra nenhuma — nenhuma das sete olha texto — e é informação que
 * quem responde pelo fiscal usa.</p>
 *
 * <h2>O sistema não compara os textos</h2>
 *
 * <p>Ele põe os dois lado a lado. Comparar automaticamente exigiria decidir o que
 * conta como "parecido", e um "não conferem" errado sobre descrição de produto
 * mandaria alguém investigar classificação correta. Pelo mesmo motivo o quadro de
 * declarado e indicado não emite veredito: quem julga são as regras, e sobre
 * texto elas não julgam nada.</p>
 *
 * <h2>A da nota é opt-in; a do catálogo não é</h2>
 *
 * <p>A do catálogo veio de arquivo que alguém importou deliberadamente. A da nota
 * é texto livre do emitente, em escala, sem revisão — ver
 * {@link PoliticaDeExposicao}. Desligada, o campo sai nulo com o motivo da
 * política; ligada e ausente, sai nulo com o motivo do documento. A tela nunca
 * recebe branco sem explicação, qualquer que seja a razão.</p>
 */
public record DescricaoComparadaExposta(
        String naNota,
        String motivoSemDescricaoNaNota,
        String noCatalogo,
        String motivoSemDescricaoNoCatalogo,
        String comoLer) {

    static final String COMO_LER =
            "As duas descrições são mostradas lado a lado para leitura. Divergência entre elas "
                    + "costuma indicar classificação errada, mas o sistema não as compara: nenhuma "
                    + "das regras cadastradas examina texto, e um veredito automático aqui não teria "
                    + "de onde sair.";

    public DescricaoComparadaExposta {
        exigirPar(naNota, motivoSemDescricaoNaNota, "naNota");
        exigirPar(noCatalogo, motivoSemDescricaoNoCatalogo, "noCatalogo");
        if (comoLer == null || comoLer.isBlank()) {
            throw new RespostaInvalida(
                    "O bloco precisa dizer como ler as duas descrições, ou a tela vira comparação que "
                            + "ninguém pediu.");
        }
    }

    static DescricaoComparadaExposta de(
            DescricaoDoProduto daNota,
            LeituraDoCatalogo<DescricaoDeNcm> doCatalogo,
            PoliticaDeExposicao politica) {

        String textoDaNota = politica.descricaoOuNulo(daNota.texto().orElse(null));
        String motivoDaNota = textoDaNota != null
                ? null
                // Ordem importa: se a instalação não expõe, o motivo é da política,
                // e não do documento. Dizer "o emitente não descreveu" quando foi a
                // configuração que omitiu seria pôr no documento uma falta que não
                // é dele.
                : primeiroNaoNulo(
                        politica.motivoDaDescricaoOmitida(),
                        daNota.motivoDaAusencia().orElse(null));

        String textoDoCatalogo = doCatalogo.encontrado().stream()
                .findFirst()
                .map(DescricaoDeNcm::descricao)
                .orElse(null);

        return new DescricaoComparadaExposta(
                textoDaNota,
                motivoDaNota,
                textoDoCatalogo,
                textoDoCatalogo != null ? null : doCatalogo.motivoDaAusencia().orElseThrow(),
                COMO_LER);
    }

    private static String primeiroNaoNulo(String preferido, String alternativo) {
        return preferido != null ? preferido : alternativo;
    }

    private static void exigirPar(String valor, String motivo, String nomeDoCampo) {
        if ((valor == null) == (motivo == null)) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" precisa ou do texto, ou do motivo de não haver texto — "
                            + "exatamente um dos dois. Descrição em branco ao lado de outra "
                            + "preenchida é lida como divergência.").formatted(nomeDoCampo));
        }
    }
}
