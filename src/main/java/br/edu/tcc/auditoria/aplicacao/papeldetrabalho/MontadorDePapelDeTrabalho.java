package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.catalogo.ConsultaDaNaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDaToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeDocumentos;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeNaoAvaliadas;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoDocumento;
import br.edu.tcc.auditoria.aplicacao.consulta.NaoAvaliadaRegistrada;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.OrigemEvidencia;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Classe que monta o papel de trabalho de uma execução a partir do que está gravado, trocando a chave de acesso pelo pseudônimo.
// Emenda de 04/10/2026 (D023): consulta a tolerância de valor da R05 que a execução usou.
// Emenda de 04/10/2026 (D021): consulta a natureza do catálogo da execução, pela mesma porta que a conferência usa.
// Emenda de 04/10/2026 (D019): consulta também quantos documentos repetidos o lote da execução descartou.
// Emenda de 04/10/2026 (D018): consulta também os arquivos que a execução não leu, para a planilha não dizer só quantos documentos entraram.
public final class MontadorDePapelDeTrabalho {

    // Separador de agrupamento, fora de qualquer texto que venha de regra ou de catálogo.
    private static final String SEPARADOR_DE_AGRUPAMENTO = String.valueOf((char) 0x1f);

    private final ConsultaDeAchadosDaExecucao achados;
    private final ConsultaDeNaoAvaliadas naoAvaliadas;
    private final ConsultaDeDocumentos documentos;
    private final PseudonimizadorDeChave pseudonimizador;
    private final ConsultaDosArquivosNaoLidos naoLidos;
    private final ConsultaDosDocumentosRepetidos repetidos;
    private final ConsultaDaNaturezaDaCarga naturezas;
    private final ConsultaDaToleranciaDaExecucao tolerancias;

    // Construtor do montador, que recebe as consultas de leitura, o pseudonimizador de chave e a consulta dos arquivos não lidos.
    public MontadorDePapelDeTrabalho(
            ConsultaDeAchadosDaExecucao achados,
            ConsultaDeNaoAvaliadas naoAvaliadas,
            ConsultaDeDocumentos documentos,
            PseudonimizadorDeChave pseudonimizador,
            ConsultaDosArquivosNaoLidos naoLidos,
            ConsultaDosDocumentosRepetidos repetidos,
            ConsultaDaNaturezaDaCarga naturezas,
            ConsultaDaToleranciaDaExecucao tolerancias) {
        this.achados = exigir(achados, "a consulta de apontamentos da execução");
        this.naoAvaliadas = exigir(naoAvaliadas, "a consulta de avaliações não concluídas");
        this.documentos = exigir(documentos, "a consulta de documentos");
        this.pseudonimizador = exigir(pseudonimizador, "o pseudonimizador de chave");
        this.naoLidos = exigir(naoLidos, "a consulta dos arquivos não lidos");
        this.repetidos = exigir(repetidos, "a consulta dos documentos repetidos");
        this.naturezas = exigir(naturezas, "a consulta da natureza do catálogo");
        this.tolerancias = exigir(tolerancias, "a consulta da tolerância da execução");
    }

    // Monta o papel de trabalho da execução indicada, com achados, não avaliados e motivos agrupados.
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
                itensDistintos(naoConcluidas),
                naoLidos.daExecucao(execucao.id()),
                repetidos.daExecucao(execucao.id()),
                naturezas.daVersao(execucao.versaoCatalogo()),
                tolerancias.daExecucao(execucao.id()));
    }

    // Método auxiliar que monta a linha de achado, separando as evidências em três listas alinhadas.
    private LinhaDeAchado linhaDe(
            AchadoRegistrado registrado, Map<ChaveAcesso, DadosDoDocumento> dados) {

        Achado achado = registrado.achado();
        DadosDoDocumento documento = exigirDocumento(dados, achado.chaveAcesso());

        List<String> campos = new ArrayList<>();
        List<Optional<String>> encontrados = new ArrayList<>();
        List<Optional<String>> esperados = new ArrayList<>();
        for (Evidencia evidencia : achado.evidencias()) {
            campos.add(evidencia.campoAnalisado());
            encontrados.add(encontrado(evidencia, achado.evidencias()));
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

    // Método auxiliar que devolve o valor encontrado da evidência. Acrescentado em 04/10/2026 (D025): execução gravada antes da correção de R01 e R06 tem o lado da tabela vazio no banco. Quando a nota informou o mesmo campo, numa evidência do documento ao lado, o vazio não pode querer dizer "não informado" — é a tabela que não tem registro, e é isso que a linha diz. Campo que a nota não trouxe, como na R07, continua vazio. Nenhuma regra é reconhecida pelo identificador.
    private static Optional<String> encontrado(Evidencia evidencia, List<Evidencia> doMesmoAchado) {
        if (evidencia.valorEncontrado().isPresent()
                || !(evidencia.origem() instanceof OrigemEvidencia.DeTabelaNormativa)) {
            return evidencia.valorEncontrado();
        }
        boolean aNotaInformouOCampo = doMesmoAchado.stream().anyMatch(outra ->
                outra.origem() instanceof OrigemEvidencia.DoDocumento
                        && outra.campoAnalisado().equals(evidencia.campoAnalisado())
                        && outra.valorEncontrado().isPresent());
        return aNotaInformouOCampo ? Optional.of(Evidencia.NENHUM_REGISTRO_NA_TABELA) : Optional.empty();
    }

    // Método auxiliar que monta a linha de não avaliado a partir da avaliação não concluída.
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

    // Método auxiliar que agrupa os motivos por regra e por texto, do mais frequente para o menos frequente.
    private static List<MotivoAgrupado> agrupar(List<NaoAvaliadaRegistrada> naoConcluidas) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        Map<String, NaoAvaliadaRegistrada> exemplos = new LinkedHashMap<>();

        for (NaoAvaliadaRegistrada naoAvaliada : naoConcluidas) {
            // Usa um separador que não ocorre em regra nem em motivo, para que textos diferentes não caiam no mesmo grupo.
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

    // Método auxiliar que conta os itens distintos atingidos por ao menos uma avaliação não concluída.
    private static int itensDistintos(List<NaoAvaliadaRegistrada> naoConcluidas) {
        Set<String> itens = new LinkedHashSet<>();
        naoConcluidas.forEach(naoAvaliada ->
                itens.add(naoAvaliada.chaveAcesso().valor() + "#" + naoAvaliada.numeroItem()));
        return itens.size();
    }

    // Método auxiliar que junta as chaves de acesso dos documentos envolvidos, sem repetição.
    private static Set<ChaveAcesso> chavesDe(
            List<AchadoRegistrado> registrados, List<NaoAvaliadaRegistrada> naoConcluidas) {
        Set<ChaveAcesso> chaves = new LinkedHashSet<>();
        registrados.forEach(registrado -> chaves.add(registrado.achado().chaveAcesso()));
        naoConcluidas.forEach(naoAvaliada -> chaves.add(naoAvaliada.chaveAcesso()));
        return chaves;
    }

    // Método auxiliar que troca a chave de acesso pelo pseudônimo dela.
    private String pseudonimoDe(ChaveAcesso chaveAcesso) {
        return pseudonimizador.de(chaveAcesso).valor();
    }

    // Método auxiliar que busca os dados do documento e falha se ele não estiver gravado.
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

    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O montador do papel de trabalho precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
