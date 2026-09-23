package br.edu.tcc.auditoria.infraestrutura.api;

import java.util.List;

// Representa um produto da nota como aparece na lista da tela de resultado. NCM e cClassTrib que faltaram vêm null com o motivo, e a situação vem sempre com rótulo, explicação, a conta que a produziu e as quatro contagens.
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

    // Motivos para NCM e cClassTrib não declarados, e o aviso de quando o item foi reprocessado depois da análise.
    static final String NCM_NAO_DECLARADO =
            "o documento não declarou NCM para este item";
    static final String CLASSTRIB_NAO_DECLARADO =
            "o documento não declarou cClassTrib para este item";
    static final String AVISO_DE_REPROCESSAMENTO =
            "este item foi reprocessado depois desta análise: os campos abaixo são os do conteúdo "
                    + "gravado hoje, e não os que esta análise leu. O sistema guarda o item, não "
                    + "versões dele.";

    // Valida o produto: endereço, documento, item, NCM e cClassTrib com valor ou motivo, valor, situação escrita, verificações, os quatro estados e o aviso de reprocessamento batendo com o sinalizador.
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

    // Representa uma verificação do produto: a regra, a versão dela quando foi gravada e o estado a que ela chegou.
    public record VerificacaoExposta(
            String regraId,
            String regraNome,
            String motivoDoNomeDaRegraAusente,
            String regraVersao,
            String motivoDaVersaoAusente,
            String estado,
            String rotuloDoEstado) {

        // Valida que haja regra, nome ou motivo, versão ou motivo, estado e rótulo.
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

    // Método auxiliar que exige valor ou motivo, nunca os dois e nunca nenhum.
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

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new RespostaInvalida(
                    "O campo \"%s\" do produto é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
