package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.conferencia.DescricaoDeNcm;
import br.edu.tcc.auditoria.aplicacao.conferencia.LeituraDoCatalogo;

// Representa a descrição que o emitente escreveu ao lado da que o catálogo dá ao NCM. O sistema não compara os textos, só mostra os dois: diferença entre eles pode indicar NCM errado, e quem julga é a pessoa. A da nota só aparece se a instalação ligar a descrição do produto.
public record DescricaoComparadaExposta(
        String naNota,
        String motivoSemDescricaoNaNota,
        String noCatalogo,
        String motivoSemDescricaoNoCatalogo,
        String comoLer) {

    // Texto que a tela mostra para explicar como ler as duas descrições.
    static final String COMO_LER =
            "As duas descrições são mostradas lado a lado para leitura. Divergência entre elas "
                    + "costuma indicar classificação errada, mas o sistema não as compara: nenhuma "
                    + "das regras cadastradas examina texto, e um veredito automático aqui não teria "
                    + "de onde sair.";

    // Valida que cada lado tenha texto ou motivo, e que haja o texto de como ler.
    public DescricaoComparadaExposta {
        exigirPar(naNota, motivoSemDescricaoNaNota, "naNota");
        exigirPar(noCatalogo, motivoSemDescricaoNoCatalogo, "noCatalogo");
        if (comoLer == null || comoLer.isBlank()) {
            throw new RespostaInvalida(
                    "O bloco precisa dizer como ler as duas descrições, ou a tela vira comparação que "
                            + "ninguém pediu.");
        }
    }

    // Método estático que monta o bloco com a descrição da nota, respeitando a política de exposição, e a do catálogo.
    static DescricaoComparadaExposta de(
            DescricaoDoProduto daNota,
            LeituraDoCatalogo<DescricaoDeNcm> doCatalogo,
            PoliticaDeExposicao politica) {

        String textoDaNota = politica.descricaoOuNulo(daNota.texto().orElse(null));
        String motivoDaNota = textoDaNota != null
                ? null
                // Se a instalação não mostra a descrição, o motivo é da configuração, e não do documento.
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

    // Método auxiliar que devolve o primeiro valor que não for null.
    private static String primeiroNaoNulo(String preferido, String alternativo) {
        return preferido != null ? preferido : alternativo;
    }

    // Método auxiliar que exige exatamente um dos dois: o texto ou o motivo.
    private static void exigirPar(String valor, String motivo, String nomeDoCampo) {
        if ((valor == null) == (motivo == null)) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" precisa ou do texto, ou do motivo de não haver texto — "
                            + "exatamente um dos dois. Descrição em branco ao lado de outra "
                            + "preenchida é lida como divergência.").formatted(nomeDoCampo));
        }
    }
}
