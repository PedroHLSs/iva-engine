package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.GrupoDeProdutos;

import java.util.List;

// Representa um grupo da tela do lote: NCM, cClassTrib e situação, com quantos produtos e notas ele alcança. O valor vai com o rótulo junto, porque é a soma dos itens envolvidos, e não o tamanho do erro; NCM ou cClassTrib que faltou vem null com o motivo.
public record GrupoExposto(
        String ncm,
        String motivoDoNcmAusente,
        String cClassTrib,
        String motivoDoClassTribAusente,
        String situacao,
        String rotuloDaSituacao,
        String explicacaoDaSituacao,
        String nivel,
        String rotuloDoNivel,
        int quantidadeDeProdutos,
        int quantidadeDeNotas,
        String valorDosProdutos,
        String rotuloDoValorDosProdutos,
        List<EstadoContado> verificacoesPorEstado,
        int produtosComAlgumaVerificacaoNaoConcluida) {

    // Motivos escritos quando a nota não declarou NCM ou cClassTrib para os itens do grupo.
    static final String NCM_NAO_DECLARADO =
            "o documento não declarou NCM para os itens deste grupo";
    static final String CLASSTRIB_NAO_DECLARADO =
            "o documento não declarou cClassTrib para os itens deste grupo";

    // Valida o grupo: NCM e cClassTrib com valor ou motivo, situação e nível escritos, contagens coerentes, valor com rótulo e os quatro estados.
    public GrupoExposto {
        if ((ncm == null) == (motivoDoNcmAusente == null)) {
            throw new RespostaInvalida(
                    "O NCM do grupo precisa ou estar presente, ou vir nulo com o motivo. Componente "
                            + "vazio e mudo faz a tela parecer ter agrupado por algo que não agrupou.");
        }
        if ((cClassTrib == null) == (motivoDoClassTribAusente == null)) {
            throw new RespostaInvalida(
                    "O cClassTrib do grupo precisa ou estar presente, ou vir nulo com o motivo.");
        }
        if (situacao == null || situacao.isBlank()
                || rotuloDaSituacao == null || rotuloDaSituacao.isBlank()
                || explicacaoDaSituacao == null || explicacaoDaSituacao.isBlank()) {
            throw new RespostaInvalida(
                    "O grupo precisa da situação com rótulo e explicação: cor nunca é a única "
                            + "codificação.");
        }
        if (nivel == null || nivel.isBlank() || rotuloDoNivel == null || rotuloDoNivel.isBlank()) {
            throw new RespostaInvalida(
                    "O grupo precisa dizer por que critério ele foi agrupado, em código e por extenso.");
        }
        if (quantidadeDeProdutos < 1) {
            throw new RespostaInvalida("Grupo sem produto não existe.");
        }
        if (quantidadeDeNotas < 1 || quantidadeDeNotas > quantidadeDeProdutos) {
            throw new RespostaInvalida(
                    "Um produto pertence a uma nota: não pode haver mais notas que produtos no grupo.");
        }
        if (valorDosProdutos == null || valorDosProdutos.isBlank()) {
            throw new RespostaInvalida(
                    "O valor dos produtos envolvidos é soma de campo obrigatório e nunca falta.");
        }
        if (rotuloDoValorDosProdutos == null || rotuloDoValorDosProdutos.isBlank()) {
            throw new RespostaInvalida(
                    "O valor sai com o rótulo dele. Sem rótulo, a coluna é lida como valor em risco.");
        }
        if (verificacoesPorEstado == null || verificacoesPorEstado.size() != 4) {
            throw new RespostaInvalida(
                    "As contagens do grupo precisam trazer os quatro estados, inclusive os zeros.");
        }
        if (produtosComAlgumaVerificacaoNaoConcluida < 0
                || produtosComAlgumaVerificacaoNaoConcluida > quantidadeDeProdutos) {
            throw new RespostaInvalida(
                    "A contagem de produtos com pendência não pode passar do total do grupo.");
        }
        verificacoesPorEstado = List.copyOf(verificacoesPorEstado);
    }

    // Método estático que converte o grupo da aplicação para a resposta.
    static GrupoExposto de(GrupoDeProdutos grupo) {
        return new GrupoExposto(
                grupo.chave().ncm().orElse(null),
                grupo.chave().ncm().isPresent() ? null : NCM_NAO_DECLARADO,
                grupo.chave().cClassTrib().orElse(null),
                grupo.chave().cClassTrib().isPresent() ? null : CLASSTRIB_NAO_DECLARADO,
                grupo.chave().situacao().name(),
                grupo.chave().situacao().rotulo(),
                grupo.chave().situacao().explicacao(),
                grupo.nivel().name(),
                grupo.nivel().rotulo(),
                grupo.quantidadeDeProdutos(),
                grupo.quantidadeDeNotas(),
                grupo.valorDosProdutos().toPlainString(),
                GrupoDeProdutos.ROTULO_DO_VALOR,
                EstadoContado.de(grupo.resumo().verificacoesPorEstado()),
                grupo.resumo().produtosComAlgumaNaoConcluida());
    }
}
