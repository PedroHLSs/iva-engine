package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MontadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MotivoAgrupado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PseudonimizadorDeChave;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.StatusDeTratativa;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

// Classe que converte o que as consultas de leitura devolvem nas respostas da API das execuções. Não cria caminho novo até o banco, e os motivos de não avaliação são agrupados pelo mesmo montador do papel de trabalho, para a planilha e a API contarem igual.
@Component
class MontadorDeRespostas {

    private final ConsultaDeExecucoes execucoes;
    private final ConsultaDeAchadosDaExecucao achadosDaExecucao;
    private final ConsultaDeNaoAvaliadas naoAvaliadas;
    private final ConsultaDeDocumentos documentos;
    private final PseudonimizadorDeChave pseudonimizador;
    private final MontadorDePapelDeTrabalho papelDeTrabalho;
    private final PoliticaDeExposicao politica;

    // Construtor que recebe as consultas de leitura, o pseudonimizador, o montador do papel de trabalho e a política de exposição.
    MontadorDeRespostas(
            ConsultaDeExecucoes execucoes,
            ConsultaDeAchadosDaExecucao achadosDaExecucao,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador,
            MontadorDePapelDeTrabalho papelDeTrabalho,
            PoliticaDeExposicao politica) {
        this.execucoes = execucoes;
        this.achadosDaExecucao = achadosDaExecucao;
        this.naoAvaliadas = naoAvaliadas;
        this.documentos = documentos;
        this.pseudonimizador = pseudonimizador;
        this.papelDeTrabalho = papelDeTrabalho;
        this.politica = politica;
    }

    // Devolve as execuções mais recentes, resumidas.
    RespostaDeExecucoes execucoes(int limite) {
        List<ExecucaoResumida> resumidas = execucoes.ultimas(limite).stream()
                .map(MontadorDeRespostas::resumir)
                .toList();
        return RespostaDeExecucoes.de(resumidas, limite);
    }

    // Devolve o detalhe de uma execução, com os três resultados e uma linha por regra; o conforme é calculado como total - achados - não avaliados.
    RespostaDaExecucao execucao(UUID id) {
        ExecucaoAuditoria execucao = exigirExecucao(id);
        PapelDeTrabalho papel = papelDeTrabalho.montar(execucao);

        int naoAvaliado = papel.quantidadeDeNaoAvaliados();
        int regrasAplicadas = execucao.achadosPorRegra().size();
        long avaliacoes = (long) execucao.quantidadeItens() * regrasAplicadas;

        return new RespostaDaExecucao(
                execucao.id().toString(),
                execucao.dataHora(),
                execucao.hashEntrada(),
                execucao.versaoCatalogo(),
                execucao.versaoConjuntoRegras(),
                execucao.quantidadeDocumentos(),
                execucao.quantidadeItens(),
                porSeveridade(execucao),
                new RespostaDaExecucao.Desfechos(
                        avaliacoes,
                        ("quantidadeItens %d x regras aplicadas %d: o motor produz exatamente uma "
                                + "avaliação por par (item, regra)")
                                .formatted(execucao.quantidadeItens(), regrasAplicadas),
                        execucao.quantidadeDeAchados(),
                        naoAvaliado,
                        ContagemDerivada.conformesDe(
                                avaliacoes,
                                execucao.quantidadeDeAchados(),
                                naoAvaliado,
                                "avaliacoesProduzidas")),
                porRegra(execucao, papel.motivosAgrupados()),
                papel.itensNaoAvaliados());
    }

    // Devolve uma página dos achados de uma execução, filtrados por regra, gravidade e situação da tratativa.
    RespostaDeAchados achados(
            UUID id,
            Optional<String> regraId,
            Optional<Severidade> severidade,
            Optional<StatusDeTratativa> status,
            int pagina,
            int tamanho) {

        exigirExecucao(id);

        List<AchadoRegistrado> filtrados = achadosDaExecucao.daExecucao(id).stream()
                .filter(registrado -> regraId
                        .map(procurada -> registrado.achado().regraId().equals(procurada))
                        .orElse(true))
                .filter(registrado -> severidade
                        .map(procurada -> registrado.achado().severidade() == procurada)
                        .orElse(true))
                .filter(registrado -> status
                        .map(procurado -> StatusDeTratativa.de(registrado.tratativa()) == procurado)
                        .orElse(true))
                .toList();

        List<AchadoRegistrado> daPagina = recortar(filtrados, pagina, tamanho);
        Map<ChaveAcesso, DadosDoDocumento> dados = documentos.porChaves(
                daPagina.stream()
                        .map(registrado -> registrado.achado().chaveAcesso())
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new RespostaDeAchados(
                id.toString(),
                PaginaExposta.de(pagina, tamanho, filtrados.size()),
                new FiltroExposto(
                        regraId.orElse(null),
                        severidade.map(Severidade::name).orElse(null),
                        status.map(StatusDeTratativa::name).orElse(null)),
                daPagina.stream().map(registrado -> expor(registrado, dados)).toList());
    }

    // Devolve uma página dos não avaliados de uma execução, podendo filtrar por regra.
    RespostaDeNaoAvaliados naoAvaliados(
            UUID id, Optional<String> regraId, int pagina, int tamanho) {

        exigirExecucao(id);

        List<NaoAvaliadaRegistrada> filtradas = naoAvaliadas.daExecucao(id).stream()
                .filter(registrada -> regraId
                        .map(procurada -> registrada.regraId().equals(procurada))
                        .orElse(true))
                .toList();

        List<NaoAvaliadaRegistrada> daPagina = recortar(filtradas, pagina, tamanho);
        Map<ChaveAcesso, DadosDoDocumento> dados = documentos.porChaves(
                daPagina.stream()
                        .map(NaoAvaliadaRegistrada::chaveAcesso)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        return new RespostaDeNaoAvaliados(
                id.toString(),
                PaginaExposta.de(pagina, tamanho, filtradas.size()),
                new FiltroExposto(regraId.orElse(null), null, null),
                daPagina.stream().map(registrada -> expor(registrada, dados)).toList());
    }

    // Método auxiliar que busca a execução; recusa se ela não existir.
    private ExecucaoAuditoria exigirExecucao(UUID id) {
        return execucoes.porId(id).orElseThrow(() -> new ExecucaoNaoEncontrada(id));
    }

    // Método auxiliar que converte a execução para a linha da listagem.
    private static ExecucaoResumida resumir(ExecucaoAuditoria execucao) {
        return new ExecucaoResumida(
                execucao.id().toString(),
                execucao.dataHora(),
                execucao.hashEntrada(),
                execucao.versaoCatalogo(),
                execucao.versaoConjuntoRegras(),
                execucao.quantidadeDocumentos(),
                execucao.quantidadeItens(),
                execucao.quantidadeDeAchados(),
                porSeveridade(execucao));
    }

    // Método auxiliar que conta os achados por gravidade, na ordem do enum. Usa LinkedHashMap, e não Map.copyOf, para a ordem não mudar de uma chamada para outra.
    private static Map<String, Integer> porSeveridade(ExecucaoAuditoria execucao) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (Severidade severidade : Severidade.values()) {
            contagem.put(severidade.name(), execucao.achadosDe(severidade));
        }
        return Collections.unmodifiableMap(contagem);
    }

    // Método auxiliar que monta uma linha por regra, ordenada pelo código. Junta as regras aplicadas com as que aparecem só nos motivos, para a soma por regra não divergir do total.
    private static List<RespostaDaExecucao.PorRegra> porRegra(
            ExecucaoAuditoria execucao, List<MotivoAgrupado> motivos) {

        Map<String, List<RespostaDaExecucao.MotivoDoNaoAvaliado>> porRegra = new TreeMap<>();
        Map<String, Integer> naoAvaliadoPorRegra = new TreeMap<>();
        for (MotivoAgrupado motivo : motivos) {
            porRegra.computeIfAbsent(motivo.regraId(), regra -> new ArrayList<>())
                    .add(new RespostaDaExecucao.MotivoDoNaoAvaliado(
                            motivo.motivo(), motivo.quantidade()));
            naoAvaliadoPorRegra.merge(motivo.regraId(), motivo.quantidade(), Integer::sum);
        }

        Set<String> regras = new TreeSet<>(execucao.achadosPorRegra().keySet());
        regras.addAll(porRegra.keySet());

        return regras.stream()
                .map(regraId -> {
                    int achado = execucao.achadosDaRegra(regraId);
                    int naoAvaliado = naoAvaliadoPorRegra.getOrDefault(regraId, 0);
                    NomeDaRegra nome = NomeDaRegra.de(regraId);
                    return new RespostaDaExecucao.PorRegra(
                            regraId,
                            nome.nome(),
                            nome.motivoDaAusencia(),
                            achado,
                            naoAvaliado,
                            ContagemDerivada.conformesDe(
                                    execucao.quantidadeItens(), achado, naoAvaliado, "quantidadeItens"),
                            porRegra.getOrDefault(regraId, List.of()));
                })
                .sorted(Comparator.comparing(RespostaDaExecucao.PorRegra::regraId))
                .toList();
    }

    // Método auxiliar que converte um achado gravado para a resposta.
    private AchadoExposto expor(
            AchadoRegistrado registrado, Map<ChaveAcesso, DadosDoDocumento> dados) {

        Achado achado = registrado.achado();
        NomeDaRegra nome = NomeDaRegra.de(achado.regraId());
        return new AchadoExposto(
                registrado.id().toString(),
                documentoDe(achado.chaveAcesso(), dados),
                achado.numeroItem().orElseThrow(() -> new RespostaInvalida(
                        ("O apontamento da regra %s não diz a que item se refere. Nenhuma das sete "
                                + "regras produz apontamento de documento inteiro, e a identidade "
                                + "gravada é a do item (D006).").formatted(achado.regraId()))),
                achado.regraId(),
                nome.nome(),
                nome.motivoDaAusencia(),
                achado.regraVersao(),
                achado.severidade().name(),
                ResultadoAvaliacao.ACHADO.name(),
                achado.evidencias().stream().map(MontadorDeRespostas::expor).toList(),
                achado.fundamentoNormativo(),
                achado.vigenciaAplicada().fim()
                        .map(fim -> AchadoExposto.VigenciaExposta.fechada(
                                achado.vigenciaAplicada().inicio(), fim))
                        .orElseGet(() -> AchadoExposto.VigenciaExposta.aberta(
                                achado.vigenciaAplicada().inicio())),
                achado.valorEmRisco().valor().map(BigDecimal::toPlainString).orElse(null),
                achado.valorEmRisco().motivoDaAusencia().orElse(null),
                registrado.detectadoEm(),
                registrado.vistoEm(),
                StatusDeTratativa.de(registrado.tratativa()).name(),
                registrado.tratativa().map(this::expor).orElse(null));
    }

    // Método auxiliar que converte a tratativa, respeitando a política de exposição da justificativa.
    private AchadoExposto.TratativaExposta expor(Tratativa tratativa) {
        return new AchadoExposto.TratativaExposta(
                tratativa.decisao().name(),
                tratativa.registradoEm(),
                politica.justificativaOuNulo(tratativa.justificativa()),
                politica.motivoDaJustificativaOmitida());
    }

    // Método auxiliar que converte uma evidência para a resposta.
    private static AchadoExposto.EvidenciaExposta expor(Evidencia evidencia) {
        return new AchadoExposto.EvidenciaExposta(
                evidencia.campoAnalisado(),
                evidencia.valorEncontrado().orElse(null),
                evidencia.valorEsperado().orElse(null),
                expor(evidencia.origem()));
    }

    // Método auxiliar que converte a origem da evidência, de acordo com o tipo.
    private static AchadoExposto.OrigemExposta expor(OrigemEvidencia origem) {
        return switch (origem) {
            case OrigemEvidencia.DoDocumento doDocumento -> new AchadoExposto.OrigemExposta(
                    "DO_DOCUMENTO", doDocumento.localizacao(), null, null, null);
            case OrigemEvidencia.DeTabelaNormativa deTabela -> new AchadoExposto.OrigemExposta(
                    "DE_TABELA_NORMATIVA", null, deTabela.nomeTabela(), deTabela.versaoTabela(), null);
            case OrigemEvidencia.DaRegra daRegra -> new AchadoExposto.OrigemExposta(
                    "DA_REGRA", null, null, null, daRegra.descricao());
        };
    }

    // Método auxiliar que converte uma avaliação não concluída para a resposta.
    private NaoAvaliadaExposta expor(
            NaoAvaliadaRegistrada registrada, Map<ChaveAcesso, DadosDoDocumento> dados) {

        NomeDaRegra nome = NomeDaRegra.de(registrada.regraId());
        return new NaoAvaliadaExposta(
                documentoDe(registrada.chaveAcesso(), dados),
                registrada.numeroItem(),
                registrada.regraId(),
                nome.nome(),
                nome.motivoDaAusencia(),
                registrada.regraVersao(),
                ResultadoAvaliacao.NAO_AVALIADO.name(),
                registrada.motivo());
    }

    // Método auxiliar que monta o documento da resposta, com o pseudônimo, e com a chave só se a política deixar.
    private DocumentoExposto documentoDe(
            ChaveAcesso chaveAcesso, Map<ChaveAcesso, DadosDoDocumento> dados) {

        DadosDoDocumento documento = dados.get(chaveAcesso);
        if (documento == null) {
            // A chave não entra na mensagem: ela carrega o CNPJ do emitente.
            throw new RespostaInvalida(
                    "Há linha sobre um documento que não está gravado. O banco foi alterado por fora, "
                            + "ou o documento foi removido depois da auditoria.");
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

    // Método auxiliar que corta a página pedida de uma lista já ordenada, em memória, sem consulta nova ao banco.
    private static <T> List<T> recortar(List<T> todos, int pagina, int tamanho) {
        // Em long, porque página alta vezes tamanho estoura int e daria erro em vez de página vazia.
        long primeiro = (long) pagina * tamanho;
        if (primeiro >= todos.size()) {
            return List.of();
        }
        int inicio = (int) primeiro;
        return todos.subList(inicio, Math.min(inicio + tamanho, todos.size()));
    }
}
