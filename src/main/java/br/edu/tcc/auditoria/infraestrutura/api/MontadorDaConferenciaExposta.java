package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.AgrupamentoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.conferencia.ChaveDoGrupo;
import br.edu.tcc.auditoria.aplicacao.conferencia.ConferenciaDaAnalise;
import br.edu.tcc.auditoria.aplicacao.conferencia.GrupoDeProdutos;
import br.edu.tcc.auditoria.aplicacao.conferencia.OrdemDosGrupos;
import br.edu.tcc.auditoria.aplicacao.conferencia.DetalheDoProduto;
import br.edu.tcc.auditoria.aplicacao.conferencia.MontadorDaConferencia;
import br.edu.tcc.auditoria.aplicacao.conferencia.ProdutoConferido;
import br.edu.tcc.auditoria.aplicacao.conferencia.VerificacaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.conferencia.VersaoDaRegra;
import br.edu.tcc.auditoria.aplicacao.catalogo.ConsultaDaNaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Traduz a conferência montada pela aplicação para o que sai na resposta.
 *
 * <p>Nenhuma decisão de conferência acontece aqui: os quatro estados, a
 * precedência e as contagens vêm prontos de {@code aplicacao.conferencia}. Esta
 * classe escolhe nomes de campo e formata número — e formatar número é onde uma
 * etapa inteira de cuidado morreria, se o valor monetário virasse número JSON.
 * Sai como texto, como na D009.</p>
 */
@Component
class MontadorDaConferenciaExposta {

    private final MontadorDaConferencia conferencias;
    private final ConsultaDeDocumentos documentos;
    private final PseudonimizadorDeChave pseudonimizador;
    private final PoliticaDeExposicao politica;
    private final MontadorDeRecibo recibos;
    private final ConsultaDaNaturezaDaCarga naturezas;

    MontadorDaConferenciaExposta(
            MontadorDaConferencia conferencias,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador,
            PoliticaDeExposicao politica,
            MontadorDeRecibo recibos,
            ConsultaDaNaturezaDaCarga naturezas) {

        this.conferencias = conferencias;
        this.documentos = documentos;
        this.pseudonimizador = pseudonimizador;
        this.politica = politica;
        this.recibos = recibos;
        this.naturezas = naturezas;
    }

    RespostaDaConferencia resultado(UUID id) {
        ConferenciaDaAnalise conferencia = exigir(id);
        return new RespostaDaConferencia(
                recibos.porId(id),
                expor(conferencia),
                faixaDe(conferencia),
                AvisoDeUso.TEXTO);
    }

    RespostaDeProdutos produtos(UUID id, int pagina, int tamanho) {
        ConferenciaDaAnalise conferencia = exigir(id);
        List<ProdutoConferido> todos = conferencia.produtos();
        List<ProdutoConferido> daPagina = recortar(todos, pagina, tamanho);

        Map<ChaveAcesso, DadosDoDocumento> dados = documentos.porChaves(daPagina.stream()
                .map(produto -> produto.dados().chaveAcesso())
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new RespostaDeProdutos(
                id.toString(),
                PaginaExposta.de(pagina, tamanho, todos.size()),
                daPagina.stream().map(produto -> expor(produto, dados)).toList(),
                faixaDe(conferencia),
                AvisoDeUso.TEXTO);
    }

    /**
     * O detalhe de um produto.
     *
     * <p>O {@code ProdutoExposto} daqui é montado pelo mesmo caminho da lista, e
     * não por um atalho: é o que garante que a situação escrita na linha e a
     * escrita no cabeçalho do detalhe sejam a mesma frase.</p>
     */
    RespostaDoDetalhe detalhe(UUID id, String endereco) {
        DetalheDoProduto detalhe = conferencias.detalhe(id, endereco)
                .orElseThrow(() -> new ProdutoNaoEncontrado(id));

        ProdutoConferido produto = detalhe.produto();
        Map<ChaveAcesso, DadosDoDocumento> dados =
                Map.of(detalhe.documento().chaveAcesso(), detalhe.documento());

        return new RespostaDoDetalhe(
                id.toString(),
                documentoDe(produto.dados().chaveAcesso(), dados),
                expor(produto, dados),
                DeclaracaoExposta.de(produto.dados().item()),
                DescricaoComparadaExposta.de(
                        produto.dados().descricao(),
                        detalhe.tratamento().descricaoDoNcm(),
                        politica),
                TratamentoExposto.de(detalhe.tratamento()),
                ComparacaoExposta.de(detalhe.comparacao()),
                detalhe.passos().stream().map(PassoExposto::de).toList(),
                FaixaDeNatureza.de(
                        naturezas.daVersao(detalhe.tratamento().versaoDoCatalogo()),
                        detalhe.tratamento().versaoDoCatalogo()),
                AvisoDeUso.TEXTO);
    }

    /**
     * A tela do lote: os grupos por parametrização, mais o resumo do lote.
     *
     * <p>Os grupos saem inteiros, sem paginação. Eles são poucos por natureza —
     * é isso que o agrupamento faz —, e paginá-los quebraria justamente a leitura
     * que a tela existe para dar: quais parametrizações alcançam mais valor. Quem
     * pagina é a lista de produtos de dentro de um grupo.</p>
     */
    RespostaDeGrupos grupos(UUID id, OrdemDosGrupos ordem) {
        ConferenciaDaAnalise conferencia = exigir(id);
        AgrupamentoDaAnalise agrupamento =
                AgrupamentoDaAnalise.de(conferencia.produtos(), ordem);

        return new RespostaDeGrupos(
                id.toString(),
                recibos.porId(id),
                expor(conferencia),
                RespostaDeGrupos.OrdemExposta.de(ordem),
                RespostaDeGrupos.todasAsOrdens(),
                agrupamento.grupos().stream().map(GrupoExposto::de).toList(),
                faixaDe(conferencia),
                AvisoDeUso.TEXTO);
    }

    /** As notas e os itens de um grupo, paginados. */
    RespostaDeProdutosDoGrupo produtosDoGrupo(
            UUID id, ChaveDoGrupo chave, int pagina, int tamanho) {

        ConferenciaDaAnalise conferencia = exigir(id);
        GrupoDeProdutos grupo = AgrupamentoDaAnalise
                .de(conferencia.produtos(), OrdemDosGrupos.padrao())
                .grupo(chave)
                .orElseThrow(() -> new GrupoNaoEncontrado(id));

        List<ProdutoConferido> daPagina = recortar(grupo.produtos(), pagina, tamanho);
        Map<ChaveAcesso, DadosDoDocumento> dados = documentos.porChaves(daPagina.stream()
                .map(produto -> produto.dados().chaveAcesso())
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new RespostaDeProdutosDoGrupo(
                id.toString(),
                GrupoExposto.de(grupo),
                PaginaExposta.de(pagina, tamanho, grupo.quantidadeDeProdutos()),
                daPagina.stream().map(produto -> expor(produto, dados)).toList(),
                faixaDe(conferencia),
                AvisoDeUso.TEXTO);
    }

    /*
     * A faixa sai da versão de catálogo que a execução registrou, e não da
     * carga mais recente: a situação exibida foi produzida contra aquela, e é a
     * procedência dela que interessa a quem lê este resultado.
     *
     * A consulta é de quatro linhas, e não carrega o catálogo. Carregá-lo para
     * desenhar uma faixa seria trazer milhares de registros por requisição.
     */
    private FaixaDeNatureza faixaDe(ConferenciaDaAnalise conferencia) {
        String versao = conferencia.execucao().versaoCatalogo();
        return FaixaDeNatureza.de(naturezas.daVersao(versao), versao);
    }

    private ConferenciaDaAnalise exigir(UUID id) {
        return conferencias.daExecucao(id).orElseThrow(() -> new ExecucaoNaoEncontrada(id));
    }

    private static ConferenciaExposta expor(ConferenciaDaAnalise conferencia) {
        return new ConferenciaExposta(
                conferencia.quantidadeDeNotas(),
                conferencia.resumo().quantidadeDeProdutos(),
                EstadoContado.de(conferencia.resumo().produtosPorSituacao()),
                conferencia.resumo().produtosComAlgumaNaoConcluida(),
                EstadoContado.de(conferencia.resumo().verificacoesPorEstado()),
                conferencia.resumo().comoFoiObtido());
    }

    private ProdutoExposto expor(
            ProdutoConferido produto, Map<ChaveAcesso, DadosDoDocumento> dados) {

        ItemDocumento item = produto.dados().item();
        boolean reprocessado = produto.dados().foiReprocessadoDepois();

        return new ProdutoExposto(
                produto.endereco(),
                documentoDe(produto.dados().chaveAcesso(), dados),
                produto.numeroItem(),
                item.ncm().map(Ncm::valor).orElse(null),
                item.ncm().isPresent() ? null : ProdutoExposto.NCM_NAO_DECLARADO,
                item.codigoClassificacaoTributaria()
                        .map(CodigoClassificacaoTributaria::valor).orElse(null),
                item.codigoClassificacaoTributaria().isPresent()
                        ? null : ProdutoExposto.CLASSTRIB_NAO_DECLARADO,
                item.valorItem().toPlainString(),
                produto.situacao().situacao().name(),
                produto.situacao().situacao().rotulo(),
                produto.situacao().situacao().explicacao(),
                produto.situacao().comoFoiObtida(),
                EstadoContado.de(produto.situacao().contagens()),
                produto.situacao().verificacoes().stream()
                        .map(MontadorDaConferenciaExposta::expor).toList(),
                reprocessado,
                reprocessado ? ProdutoExposto.AVISO_DE_REPROCESSAMENTO : null);
    }

    private static ProdutoExposto.VerificacaoExposta expor(VerificacaoDoProduto verificacao) {
        NomeDaRegra nome = NomeDaRegra.de(verificacao.regraId());
        return switch (verificacao.versao()) {
            case VersaoDaRegra.Registrada registrada -> new ProdutoExposto.VerificacaoExposta(
                    verificacao.regraId(),
                    nome.nome(),
                    nome.motivoDaAusencia(),
                    registrada.valor(),
                    null,
                    verificacao.estado().name(),
                    verificacao.estado().rotulo());
            case VersaoDaRegra.NaoRegistrada ausente -> new ProdutoExposto.VerificacaoExposta(
                    verificacao.regraId(),
                    nome.nome(),
                    nome.motivoDaAusencia(),
                    null,
                    ausente.motivo(),
                    verificacao.estado().name(),
                    verificacao.estado().rotulo());
        };
    }

    /*
     * Duplicado de MontadorDeRespostas, da Etapa 8, e não extraído.
     *
     * Compartilhar obrigaria a alargar a visibilidade de um método privado
     * daquela classe, que é a montagem das respostas de leitura e tem contrato
     * próprio afirmado em teste. São quinze linhas; a duplicação é mais barata
     * que o acoplamento, e é o mesmo julgamento que a revisão das Etapas 0 a 4
     * fez ao duplicar o descarte de comentários entre dois guardas.
     */
    private DocumentoExposto documentoDe(
            ChaveAcesso chaveAcesso, Map<ChaveAcesso, DadosDoDocumento> dados) {

        DadosDoDocumento documento = dados.get(chaveAcesso);
        if (documento == null) {
            // A chave não entra na mensagem: ela carrega o CNPJ do emitente.
            throw new RespostaInvalida(
                    "Há produto de um documento que não está gravado. O banco foi alterado por fora, "
                            + "ou o documento foi removido depois da análise.");
        }
        return new DocumentoExposto(
                pseudonimizador.de(chaveAcesso).valor(),
                politica.chaveOuNulo(chaveAcesso.valor()),
                politica.motivoDaChaveOmitida(),
                documento.modelo(),
                documento.serie(),
                documento.numero(),
                documento.dataEmissao(),
                documento.ufEmitente().name());
    }

    private static <T> List<T> recortar(List<T> todos, int pagina, int tamanho) {
        long primeiro = (long) pagina * tamanho;
        if (primeiro >= todos.size()) {
            return List.of();
        }
        int inicio = (int) primeiro;
        return new ArrayList<>(todos.subList(inicio, Math.min(inicio + tamanho, todos.size())));
    }
}
