package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoItem;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Peças fictícias para os testes de conferência.
 *
 * <p>Nenhum valor aqui pode ser lido como afirmação sobre a legislação:
 * identificadores de regra com sufixo "ficticia", fundamento em caixa alta
 * dizendo que é fictício, vigência em 1900 e chave de acesso de dígitos
 * repetidos.</p>
 */
final class ConferenciaFicticia {

    static final String FUNDAMENTO = "FUNDAMENTO FICTICIO PARA TESTE";
    static final String REGRA_VERSAO = "0.0.0-ficticia";
    static final String DESCRICAO = "PRODUTO FICTICIO DE TESTE";

    private ConferenciaFicticia() {
    }

    static ChaveAcesso chave() {
        return new ChaveAcesso("9".repeat(44));
    }

    static Achado achado(String regraId, Severidade severidade) {
        return new Achado(
                regraId,
                REGRA_VERSAO,
                severidade,
                chave(),
                OptionalInt.of(1),
                List.of(new Evidencia(
                        "campoFicticio",
                        Optional.of("99,99"),
                        Optional.of("00,00"),
                        new OrigemEvidencia.DaRegra("derivação fictícia para teste"))),
                FUNDAMENTO,
                PeriodoVigencia.de(LocalDate.of(1900, 1, 1), LocalDate.of(1900, 12, 31)),
                ValorEmRisco.naoCalculavel("valor fictício não calculável para teste"));
    }

    static Avaliacao comAchado(String regraId, Severidade severidade) {
        return Avaliacao.comAchado(achado(regraId, severidade));
    }

    static Avaliacao conforme(String regraId) {
        return Avaliacao.conforme(regraId, REGRA_VERSAO, chave(), OptionalInt.of(1));
    }

    static Avaliacao naoAvaliada(String regraId) {
        return Avaliacao.naoAvaliada(
                regraId, REGRA_VERSAO, chave(), OptionalInt.of(1),
                "motivo fictício: faltou dado no cenário de teste");
    }

    static VerificacaoDoProduto verificacao(String regraId, EstadoDeConferencia estado) {
        return new VerificacaoDoProduto(
                regraId, VersaoDaRegra.registrada(REGRA_VERSAO), estado);
    }

    /** Uma verificação cuja versão de regra não está gravada — o caso do conforme. */
    static VerificacaoDoProduto semVersaoRegistrada(String regraId, EstadoDeConferencia estado) {
        return new VerificacaoDoProduto(
                regraId,
                VersaoDaRegra.naoRegistrada("motivo fictício: nada foi gravado para esta verificação"),
                estado);
    }

    /**
     * Um item fictício, com NCM e cClassTrib opcionais.
     *
     * <p>NCM de oito zeros e código com sufixo "-FICT": nenhum dos dois pode ser
     * lido como afirmação sobre a legislação.</p>
     */
    static ItemDocumento item(int numeroItem, String ncm, String classTrib, String valorItem) {
        return new ItemDocumento(
                numeroItem,
                Optional.ofNullable(ncm).map(Ncm::new),
                Optional.empty(),
                new BigDecimal(valorItem),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(classTrib).map(CodigoClassificacaoTributaria::new),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Uma segunda chave fictícia, para separar notas sem inventar documento real. */
    static ChaveAcesso outraChave() {
        return new ChaveAcesso("8".repeat(44));
    }

    /** Um produto conferido sem apontamento nem pendência gravados. */
    static ProdutoConferido produto(ItemDocumento item, SituacaoDoProduto situacao) {
        return produto(chave(), item, situacao);
    }

    /** O mesmo, com a nota escolhida — o que separa contagem de notas de contagem de produtos. */
    static ProdutoConferido produto(
            ChaveAcesso chaveAcesso, ItemDocumento item, SituacaoDoProduto situacao) {
        HashDoItem resumo = HashDoItem.de(chaveAcesso, item);
        return new ProdutoConferido(
                new DadosDoItem(chaveAcesso, item, descricaoFicticia(), resumo, resumo),
                situacao,
                List.of(),
                List.of());
    }

    /** Um apontamento como ele volta do banco. */
    static AchadoRegistrado registrado(String regraId, Severidade severidade) {
        Achado achado = achado(regraId, severidade);
        return new AchadoRegistrado(
                java.util.UUID.nameUUIDFromBytes(regraId.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                achado,
                new HashDoItem("0".repeat(64)),
                Optional.empty(),
                java.time.Instant.EPOCH,
                java.time.Instant.EPOCH);
    }

    /** Uma pendência como ela volta do banco, com o motivo que a regra escreveu. */
    static NaoAvaliadaRegistrada pendencia(String regraId, int numeroItem) {
        return new NaoAvaliadaRegistrada(
                chave(),
                numeroItem,
                regraId,
                REGRA_VERSAO,
                "motivo fictício: faltou dado no cenário de teste");
    }

    /** Um produto com apontamento e pendência gravados, para o detalhe ter o que explicar. */
    static ProdutoConferido produtoComRegistros(
            ItemDocumento item,
            SituacaoDoProduto situacao,
            List<AchadoRegistrado> achados,
            List<NaoAvaliadaRegistrada> pendencias) {

        HashDoItem resumo = HashDoItem.de(chave(), item);
        return new ProdutoConferido(
                new DadosDoItem(chave(), item, descricaoFicticia(), resumo, resumo),
                situacao, achados, pendencias);
    }

    /** A descrição que o emitente teria escrito, obviamente fictícia. */
    static DescricaoDoProduto descricaoFicticia() {
        return DescricaoDoProduto.de(Optional.of(DESCRICAO));
    }

    /** Um produto cujas verificações produzem exatamente os estados dados. */
    static SituacaoDoProduto produtoCom(EstadoDeConferencia... estados) {
        List<VerificacaoDoProduto> verificacoes = new java.util.ArrayList<>();
        for (int posicao = 0; posicao < estados.length; posicao++) {
            verificacoes.add(verificacao("RXX%02d".formatted(posicao + 1), estados[posicao]));
        }
        return new SituacaoDoProduto(List.copyOf(verificacoes));
    }
}
