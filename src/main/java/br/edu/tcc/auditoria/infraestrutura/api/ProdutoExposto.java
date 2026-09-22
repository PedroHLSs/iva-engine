package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

/**
 * Um produto de uma nota, como ele aparece na lista da tela de resultado.
 *
 * <h2>Ausência é null com o campo irmão dizendo por quê</h2>
 *
 * <p>Vale para NCM e {@code cClassTrib}, que são {@code Optional} no item porque
 * o documento pode não os ter declarado. O par é garantido no construtor, e não
 * na disciplina de quem monta — é a mesma técnica da Etapa 8.</p>
 *
 * <h2>A situação nunca vem sozinha</h2>
 *
 * <p>Junto dela vão o rótulo por extenso, a explicação, a conta que a produziu e
 * as quatro contagens deste produto. Uma palavra só numa coluna, ao lado de uma
 * cor, é a forma extrema de cor ser a única codificação.</p>
 */
public record ProdutoExposto(
        String endereco,
        DocumentoExposto documento,
        int numeroItem,
        String ncm,
        String motivoDoNcmAusente,
        String cClassTrib,
        String motivoDoClassTribAusente,
        String valorDoProduto,
        String situacao,
        String rotuloDaSituacao,
        String explicacaoDaSituacao,
        String comoASituacaoFoiObtida,
        List<EstadoContado> verificacoesPorEstado,
        List<VerificacaoExposta> verificacoes,
        boolean reprocessadoDepoisDestaAnalise,
        String avisoDeReprocessamento) {

    static final String NCM_NAO_DECLARADO =
            "o documento não declarou NCM para este item";
    static final String CLASSTRIB_NAO_DECLARADO =
            "o documento não declarou cClassTrib para este item";
    static final String AVISO_DE_REPROCESSAMENTO =
            "este item foi reprocessado depois desta análise: os campos abaixo são os do conteúdo "
                    + "gravado hoje, e não os que esta análise leu. O sistema guarda o item, não "
                    + "versões dele.";

    public ProdutoExposto {
        if (endereco == null || endereco.isBlank()) {
            throw new RespostaInvalida(
                    "O produto precisa do endereço estável dele: é por ele que a tela de detalhe o "
                            + "encontra, e ele não carrega identificador em texto claro.");
        }
        if (documento == null) {
            throw new RespostaInvalida("O produto precisa dizer de que documento ele é.");
        }
        if (numeroItem < 1) {
            throw new RespostaInvalida(
                    "O número do item deve ser maior ou igual a 1, mas veio %d.".formatted(numeroItem));
        }
        exigirParDeAusencia(ncm, motivoDoNcmAusente, "ncm");
        exigirParDeAusencia(cClassTrib, motivoDoClassTribAusente, "cClassTrib");
        if (valorDoProduto == null || valorDoProduto.isBlank()) {
            throw new RespostaInvalida(
                    "O valor do produto é obrigatório no item e por isso nunca é nulo aqui.");
        }
        exigirTexto(situacao, "situacao");
        exigirTexto(rotuloDaSituacao, "rotuloDaSituacao");
        exigirTexto(explicacaoDaSituacao, "explicacaoDaSituacao");
        exigirTexto(comoASituacaoFoiObtida, "comoASituacaoFoiObtida");
        if (verificacoes == null || verificacoes.isEmpty()) {
            throw new RespostaInvalida(
                    "Um produto sem nenhuma verificação não tem situação, e não pode aparecer com "
                            + "uma. Item sem verificação é item que não foi auditado.");
        }
        if (verificacoesPorEstado == null || verificacoesPorEstado.size() != 4) {
            throw new RespostaInvalida(
                    "As contagens do produto precisam trazer os quatro estados, inclusive os zeros.");
        }
        if (reprocessadoDepoisDestaAnalise == (avisoDeReprocessamento == null)) {
            throw new RespostaInvalida(
                    "Ou o item foi reprocessado e há o aviso, ou não foi e não há. As duas coisas "
                            + "precisam concordar: um sinalizador sem frase seria um alerta que a "
                            + "tela teria de inventar.");
        }
        verificacoes = List.copyOf(verificacoes);
        verificacoesPorEstado = List.copyOf(verificacoesPorEstado);
    }

    /**
     * Uma regra, a versão dela quando gravada, e o que ela concluiu.
     *
     * <p>{@code regraNome} e {@code motivoDoNomeDaRegraAusente} foram acrescentados
     * depois da Etapa 11, para a resposta não trazer só o código: ver
     * {@link NomeDaRegra}.</p>
     */
    public record VerificacaoExposta(
            String regraId,
            String regraNome,
            String motivoDoNomeDaRegraAusente,
            String regraVersao,
            String motivoDaVersaoAusente,
            String estado,
            String rotuloDoEstado) {

        public VerificacaoExposta {
            if (regraId == null || regraId.isBlank()) {
                throw new RespostaInvalida("A verificação precisa do identificador da regra.");
            }
            NomeDaRegra.exigirPar(regraNome, motivoDoNomeDaRegraAusente, regraId);
            exigirParDeAusencia(regraVersao, motivoDaVersaoAusente, "regraVersao");
            if (estado == null || estado.isBlank()
                    || rotuloDoEstado == null || rotuloDoEstado.isBlank()) {
                throw new RespostaInvalida(
                        "A verificação da regra %s precisa do estado e do rótulo dele."
                                .formatted(regraId));
            }
        }
    }

    private static void exigirParDeAusencia(String valor, String motivo, String nomeDoCampo) {
        if (valor == null && (motivo == null || motivo.isBlank())) {
            throw new RespostaInvalida(
                    ("O campo \"%s\" veio nulo sem dizer por quê. Campo em branco sem explicação é o "
                            + "silêncio que esta camada existe para evitar.").formatted(nomeDoCampo));
        }
        if (valor != null && motivo != null) {
            throw new RespostaInvalida(
                    "O campo \"%s\" não pode estar presente e ausente ao mesmo tempo."
                            .formatted(nomeDoCampo));
        }
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    "O campo \"%s\" do produto é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
