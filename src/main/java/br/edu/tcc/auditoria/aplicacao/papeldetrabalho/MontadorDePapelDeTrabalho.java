package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Monta o papel de trabalho de uma execução a partir do que está gravado.
 *
 * <p>Junta quatro coisas: o recibo da execução, os apontamentos que ela produziu
 * com a tratativa atual de cada um, as avaliações que não concluíram, e os dados
 * de identificação dos documentos envolvidos.</p>
 *
 * <h2>A chave de acesso não sai daqui</h2>
 *
 * <p>Ela é substituída pelo pseudônimo antes de qualquer linha ser montada. Os
 * dígitos da chave carregam o CNPJ do emitente, e a exportação não leva
 * identificador em texto claro. O que fica no lugar — modelo, série, número,
 * data e UF — localiza a nota no sistema da empresa sem identificar ninguém.</p>
 */
public final class MontadorDePapelDeTrabalho {

    /** Separador de agrupamento, fora de qualquer texto que venha de regra ou de catálogo. */
    private static final String SEPARADOR_DE_AGRUPAMENTO = String.valueOf((char) 0x1f);

    private final ConsultaDeAchadosDaExecucao achados;
    private final ConsultaDeNaoAvaliadas naoAvaliadas;
    private final ConsultaDeDocumentos documentos;
    private final PseudonimizadorDeChave pseudonimizador;

    public MontadorDePapelDeTrabalho(
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador) {
        this.achados = exigir(achados, "a consulta de apontamentos da execução");
        this.naoAvaliadas = exigir(naoAvaliadas, "a consulta de avaliações não concluídas");
        this.documentos = exigir(documentos, "a consulta de documentos");
        this.pseudonimizador = exigir(pseudonimizador, "o pseudonimizador de chave");
    }

    /** Monta o papel de trabalho da execução indicada. */
    public PapelDeTrabalho montar(ExecucaoAuditoria execucao) {
        if (execucao == null) {
            throw new PapelDeTrabalhoInvalido("Não há execução cujo papel de trabalho montar.");
        }

        List<AchadoRegistrado> registrados = achados.daExecucao(execucao.id());
        List<NaoAvaliadaRegistrada> naoConcluidas = naoAvaliadas.daExecucao(execucao.id());
        Map<ChaveAcesso, DadosDoDocumento> dados = documentos.porChaves(chavesDe(registrados, naoConcluidas));

        return new PapelDeTrabalho(
                execucao,
                registrados.stream().map(registrado -> linhaDe(registrado, dados)).toList(),
                naoConcluidas.stream().map(naoAvaliada -> linhaDe(naoAvaliada, dados)).toList(),
                agrupar(naoConcluidas),
                itensDistintos(naoConcluidas));
    }

    private LinhaDeAchado linhaDe(
            AchadoRegistrado registrado, Map<ChaveAcesso, DadosDoDocumento> dados) {

        Achado achado = registrado.achado();
        DadosDoDocumento documento = exigirDocumento(dados, achado.chaveAcesso());

        List<String> campos = new ArrayList<>();
        List<Optional<String>> encontrados = new ArrayList<>();
        List<Optional<String>> esperados = new ArrayList<>();
        for (Evidencia evidencia : achado.evidencias()) {
            campos.add(evidencia.campoAnalisado());
            encontrados.add(evidencia.valorEncontrado());
            esperados.add(evidencia.valorEsperado());
        }

        return new LinhaDeAchado(
                pseudonimoDe(achado.chaveAcesso()),
                documento.modelo(),
                documento.serie(),
                documento.numero(),
                documento.dataEmissao(),
                documento.ufEmitente(),
                achado.numeroItem().orElseThrow(() -> new PapelDeTrabalhoInvalido(
                        ("O apontamento da regra %s não diz a que item se refere. A planilha precisa "
                                + "disso para ser conferível.").formatted(achado.regraId()))),
                achado.regraId(),
                achado.regraVersao(),
                achado.severidade(),
                campos,
                encontrados,
                esperados,
                achado.fundamentoNormativo(),
                achado.vigenciaAplicada().inicio(),
                achado.vigenciaAplicada().fim(),
                achado.valorEmRisco().valor(),
                achado.valorEmRisco().motivoDaAusencia(),
                StatusDeTratativa.de(registrado.tratativa()),
                registrado.tratativa().map(tratativa -> tratativa.justificativa()),
                registrado.tratativa().map(tratativa -> tratativa.registradoEm()));
    }

    private LinhaNaoAvaliada linhaDe(
            NaoAvaliadaRegistrada naoAvaliada, Map<ChaveAcesso, DadosDoDocumento> dados) {

        DadosDoDocumento documento = exigirDocumento(dados, naoAvaliada.chaveAcesso());
        return new LinhaNaoAvaliada(
                pseudonimoDe(naoAvaliada.chaveAcesso()),
                documento.modelo(),
                documento.serie(),
                documento.numero(),
                naoAvaliada.numeroItem(),
                naoAvaliada.regraId(),
                naoAvaliada.regraVersao(),
                naoAvaliada.motivo());
    }

    /**
     * Agrupa os motivos por regra e por texto, do mais frequente para o menos.
     *
     * <p>Milhares de linhas de não avaliado quase sempre são poucos motivos
     * repetidos. Ordenar por frequência coloca no topo o que, resolvido, elimina
     * a maior parte delas.</p>
     */
    private static List<MotivoAgrupado> agrupar(List<NaoAvaliadaRegistrada> naoConcluidas) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        Map<String, NaoAvaliadaRegistrada> exemplos = new LinkedHashMap<>();

        for (NaoAvaliadaRegistrada naoAvaliada : naoConcluidas) {
            // Separador que nao ocorre em identificador de regra nem em texto de
            // motivo: sem ele, "R0" + "5x" e "R05" + "x" cairiam no mesmo grupo.
            String chave = naoAvaliada.regraId() + SEPARADOR_DE_AGRUPAMENTO + naoAvaliada.motivo();
            contagem.merge(chave, 1, Integer::sum);
            exemplos.putIfAbsent(chave, naoAvaliada);
        }

        return contagem.entrySet().stream()
                .map(entrada -> new MotivoAgrupado(
                        exemplos.get(entrada.getKey()).regraId(),
                        exemplos.get(entrada.getKey()).motivo(),
                        entrada.getValue()))
                .sorted(Comparator.comparingInt(MotivoAgrupado::quantidade).reversed()
                        .thenComparing(MotivoAgrupado::regraId)
                        .thenComparing(MotivoAgrupado::motivo))
                .toList();
    }

    /** Itens distintos atingidos por ao menos uma avaliação não concluída. */
    private static int itensDistintos(List<NaoAvaliadaRegistrada> naoConcluidas) {
        Set<String> itens = new LinkedHashSet<>();
        naoConcluidas.forEach(naoAvaliada ->
                itens.add(naoAvaliada.chaveAcesso().valor() + "#" + naoAvaliada.numeroItem()));
        return itens.size();
    }

    private static Set<ChaveAcesso> chavesDe(
            List<AchadoRegistrado> registrados, List<NaoAvaliadaRegistrada> naoConcluidas) {
        Set<ChaveAcesso> chaves = new LinkedHashSet<>();
        registrados.forEach(registrado -> chaves.add(registrado.achado().chaveAcesso()));
        naoConcluidas.forEach(naoAvaliada -> chaves.add(naoAvaliada.chaveAcesso()));
        return chaves;
    }

    private String pseudonimoDe(ChaveAcesso chaveAcesso) {
        return pseudonimizador.de(chaveAcesso).valor();
    }

    private static DadosDoDocumento exigirDocumento(
            Map<ChaveAcesso, DadosDoDocumento> dados, ChaveAcesso chaveAcesso) {
        DadosDoDocumento documento = dados.get(chaveAcesso);
        if (documento == null) {
            // A chave não entra na mensagem: ela carrega o CNPJ do emitente.
            throw new PapelDeTrabalhoInvalido(
                    "Há apontamento sobre um documento que não está gravado. O banco foi alterado por "
                            + "fora, ou o documento foi removido depois da auditoria.");
        }
        return documento;
    }

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O montador do papel de trabalho precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
