package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogoPorVersao;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeItensDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoItem;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

// Classe que reconstrói, a partir do que foi gravado, os quatro estados de cada produto; o sem divergência é derivado por subtração.
public final class MontadorDaConferencia {

    private final ConsultaDeExecucoes execucoes;
    private final ConsultaDeItensDaExecucao itens;
    private final ConsultaDeAchadosDaExecucao achados;
    private final ConsultaDeNaoAvaliadas naoAvaliadas;
    private final ConsultaDoAcervoDaAnalise acervo;
    private final ConsultaDeDocumentos documentos;
    private final ProvedorDeCatalogoPorVersao catalogos;

    // Construtor do montador, que recebe as consultas de leitura e o provedor de catálogo por versão.
    public MontadorDaConferencia(
            ConsultaDeExecucoes execucoes,
            ConsultaDeItensDaExecucao itens,
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDoAcervoDaAnalise acervo,
            ConsultaDeDocumentos documentos,
            ProvedorDeCatalogoPorVersao catalogos) {

        this.execucoes = exigir(execucoes, "a consulta de execuções");
        this.itens = exigir(itens, "a consulta de itens");
        this.achados = exigir(achados, "a consulta de apontamentos");
        this.naoAvaliadas = exigir(naoAvaliadas, "a consulta de avaliações não concluídas");
        this.acervo = exigir(acervo, "a consulta do acervo da análise");
        this.documentos = exigir(documentos, "a consulta de documentos");
        this.catalogos = exigir(catalogos, "o provedor de catálogo por versão");
    }

    // Retorna a conferência da análise inteira, ou vazio se ela não existe.
    public Optional<ConferenciaDaAnalise> daExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            throw new ConferenciaInvalida("Não há análise a montar.");
        }
        return execucoes.porId(execucaoId).map(this::montar);
    }

    // Retorna o detalhe de um produto, montado a partir da mesma conferência da lista e resolvido na carga e na data daquela análise.
    public Optional<DetalheDoProduto> detalhe(UUID execucaoId, String endereco) {
        if (endereco == null || endereco.isBlank()) {
            throw new ConferenciaInvalida("Não há endereço de produto a detalhar.");
        }
        Optional<ConferenciaDaAnalise> encontrada = daExecucao(execucaoId);
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        ConferenciaDaAnalise conferencia = encontrada.orElseThrow();
        Optional<ProdutoConferido> encontrado = conferencia.produto(endereco);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        ProdutoConferido produto = encontrado.orElseThrow();
        DadosDoDocumento documento = documentoDe(produto);
        BaseNormativa base = BaseNormativa.daVersao(
                catalogos, conferencia.execucao().versaoCatalogo());
        TratamentoIdentificado tratamento = base.tratamentoDe(
                documento.dataEmissao(), produto.dados().item());

        return Optional.of(new DetalheDoProduto(
                documento,
                produto,
                tratamento,
                ComparacaoDeclaradoEIndicado.de(produto.dados().item(), tratamento),
                PassoDaConferencia.de(produto)));
    }

    // Método auxiliar que busca o documento do produto, necessário para a data de emissão.
    private DadosDoDocumento documentoDe(ProdutoConferido produto) {
        ChaveAcesso chave = produto.dados().chaveAcesso();
        DadosDoDocumento documento = documentos.porChaves(List.of(chave)).get(chave);
        if (documento == null) {
            // A chave não entra na mensagem: ela carrega o CNPJ do emitente.
            throw new ConferenciaInvalida(
                    "Há produto de um documento que não está mais gravado. Sem a data de emissão "
                            + "dele não há como resolver o catálogo, e resolver noutra data seria "
                            + "fundamentar o resultado com norma que não o produziu.");
        }
        return documento;
    }

    // Método auxiliar que monta a conferência, agrupando apontamentos e pendências por item.
    private ConferenciaDaAnalise montar(ExecucaoAuditoria execucao) {
        UUID id = execucao.id();

        Map<String, List<AchadoRegistrado>> achadosPorItem = new LinkedHashMap<>();
        for (AchadoRegistrado registrado : achados.daExecucao(id)) {
            Achado achado = registrado.achado();
            achado.numeroItem().ifPresent(numero -> achadosPorItem
                    .computeIfAbsent(endereco(achado.chaveAcesso().valor(), numero),
                            chave -> new ArrayList<>())
                    .add(registrado));
        }

        Map<String, List<NaoAvaliadaRegistrada>> pendenciasPorItem = new LinkedHashMap<>();
        for (NaoAvaliadaRegistrada pendencia : naoAvaliadas.daExecucao(id)) {
            pendenciasPorItem
                    .computeIfAbsent(endereco(pendencia.chaveAcesso().valor(), pendencia.numeroItem()),
                            chave -> new ArrayList<>())
                    .add(pendencia);
        }

        List<ProdutoConferido> produtos = new ArrayList<>();
        for (DadosDoItem item : itens.daExecucao(id)) {
            String endereco = endereco(item.chaveAcesso().valor(), item.numeroItem());
            List<AchadoRegistrado> doItem = achadosPorItem.getOrDefault(endereco, List.of());
            List<NaoAvaliadaRegistrada> pendentes = pendenciasPorItem.getOrDefault(endereco, List.of());
            produtos.add(new ProdutoConferido(
                    item, situacaoDe(execucao, doItem, pendentes), doItem, pendentes));
        }

        return new ConferenciaDaAnalise(
                execucao,
                ResumoDaConferencia.de(produtos.stream().map(ProdutoConferido::situacao).toList()),
                produtos,
                acervo.arquivosIlegiveis(id));
    }

    // Método auxiliar que monta as verificações de um item: as gravadas, mais as derivadas por subtração.
    private static SituacaoDoProduto situacaoDe(
            ExecucaoAuditoria execucao,
            List<AchadoRegistrado> doItem,
            List<NaoAvaliadaRegistrada> pendentes) {

        List<VerificacaoDoProduto> verificacoes = new ArrayList<>();

        for (AchadoRegistrado registrado : doItem) {
            Achado achado = registrado.achado();
            verificacoes.add(new VerificacaoDoProduto(
                    achado.regraId(),
                    VersaoDaRegra.registrada(achado.regraVersao()),
                    TraducaoDeDesfecho.de(
                            ResultadoAvaliacao.ACHADO, Optional.of(achado.severidade()))));
        }

        for (NaoAvaliadaRegistrada pendencia : pendentes) {
            verificacoes.add(new VerificacaoDoProduto(
                    pendencia.regraId(),
                    VersaoDaRegra.registrada(pendencia.regraVersao()),
                    TraducaoDeDesfecho.de(ResultadoAvaliacao.NAO_AVALIADO, Optional.empty())));
        }

        // Percorre as regras num TreeSet para que a derivação saia sempre na mesma sequência.
        for (String regraId : new TreeSet<>(execucao.achadosPorRegra().keySet())) {
            if (jaTemVerificacao(verificacoes, regraId)) {
                continue;
            }
            verificacoes.add(new VerificacaoDoProduto(
                    regraId,
                    VersaoDaRegra.naoRegistrada(motivoDaVersaoAusente(regraId)),
                    TraducaoDeDesfecho.de(ResultadoAvaliacao.CONFORME, Optional.empty())));
        }

        return new SituacaoDoProduto(verificacoes);
    }

    // Método auxiliar que verifica se a regra já tem verificação neste item.
    private static boolean jaTemVerificacao(List<VerificacaoDoProduto> verificacoes, String regraId) {
        return verificacoes.stream().anyMatch(verificacao -> verificacao.regraId().equals(regraId));
    }

    // Método auxiliar que escreve por que a versão da regra não está registrada no resultado derivado.
    private static String motivoDaVersaoAusente(String regraId) {
        return ("A regra %s foi aplicada nesta execução e não deixou apontamento nem avaliação não "
                + "concluída para este item, então ela concluiu sem encontrar violação. O banco não "
                + "grava avaliação conforme, de modo que não há linha de onde ler a versão da regra: "
                + "ela é a do conjunto que a execução registra, mas afirmá-la aqui dependeria de o "
                + "código de hoje ainda montar aquele conjunto.").formatted(regraId);
    }

    // Método auxiliar que monta o endereço interno do item, pela chave de acesso e o número do item.
    private static String endereco(String chaveAcesso, int numeroItem) {
        return chaveAcesso + "#" + numeroItem;
    }

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new ConferenciaInvalida("O montador da conferência precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
