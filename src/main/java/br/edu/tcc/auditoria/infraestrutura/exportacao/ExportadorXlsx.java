package br.edu.tcc.auditoria.infraestrutura.exportacao;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.SituacaoDaNatureza;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.ExportadorDePapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaDeAchado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.LinhaNaoAvaliada;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.MotivoAgrupado;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalho;
import br.edu.tcc.auditoria.aplicacao.papeldetrabalho.PapelDeTrabalhoInvalido;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Classe que grava o papel de trabalho em xlsx, com três abas: Resumo, que começa pela identificação da execução, Achados e Não avaliados. Escreve em fluxo, com poucas linhas na memória, por isso as larguras das colunas são fixas. Emenda da Etapa 12: a justificativa da tratativa só vai para a planilha se a instalação ligar auditoria.exportacao.expor-justificativa; desligada, a célula traz o motivo da omissão. Até a Etapa 11 ela saía sempre, e o texto do projeto afirmava um opt-in que não existia.
// Emenda de 04/10/2026 (D023): a identificação ganhou "Tolerância de valor (R05)", logo abaixo da versão do conjunto de regras, com a origem — o padrão escrito como padrão —, ou "(não registrada: …)".
// Emenda de 04/10/2026 (D021): toda aba abre com a faixa de procedência do catálogo na primeira linha — a situação por extenso, a explicação, as tabelas fictícias e as sem natureza declarada —, com destaque quando há aviso, e o Resumo traz a natureza na identificação da execução. O cabeçalho das abas de linhas desceu para a segunda linha. Até essa data a planilha não dizia em lugar nenhum que o catálogo era fictício.
// Emenda de 04/10/2026 (D019): a identificação ganhou a linha "Documentos repetidos descartados", logo abaixo dos arquivos não lidos; sem a contagem registrada, ela diz isso em vez de zero.
// Emenda de 04/10/2026 (D018): a identificação ganhou a linha "Arquivos que não puderam ser lidos", logo abaixo dos itens, e a planilha ganhou a quarta aba, Não lidos, com cada arquivo. Quando a leitura da execução não foi registrada, as duas dizem isso, em vez de zero. Até essa data a planilha dizia "Documentos auditados" e não mencionava os arquivos que ficaram de fora.
@Component
class ExportadorXlsx implements ExportadorDePapelDeTrabalho {

    static final String ABA_RESUMO = "Resumo";
    static final String ABA_ACHADOS = "Achados";
    static final String ABA_NAO_AVALIADOS = "Não avaliados";
    static final String ABA_NAO_LIDOS = "Não lidos";

    static final String ROTULO_DOS_NAO_LIDOS = "Arquivos que não puderam ser lidos";
    static final String LEITURA_NAO_REGISTRADA =
            "(não registrado: esta execução não gravou quais arquivos deixaram de ser lidos, e zero "
                    + "afirmaria que nenhum falhou)";
    static final String NENHUM_NAO_LIDO = "Nenhum arquivo deixou de ser lido.";

    static final String ROTULO_DA_TOLERANCIA = "Tolerância de valor (R05)";
    static final String ROTULO_DA_NATUREZA = "Natureza do catálogo";
    static final String ROTULO_DAS_FICTICIAS = "Tabelas fictícias";
    static final String ROTULO_DAS_SEM_NATUREZA = "Tabelas sem natureza declarada";

    // Linha do cabeçalho nas abas de linhas; a de cima é a faixa.
    static final int LINHA_DO_CABECALHO = 1;

    static final String ROTULO_DOS_REPETIDOS = "Documentos repetidos descartados";
    static final String REPETIDOS_NAO_REGISTRADOS =
            "(não registrado: esta execução não gravou quantos documentos repetidos o lote descartou)";

    private static final List<String> CABECALHO_DE_NAO_LIDOS = List.of("Arquivo", "Tipo de erro", "Motivo");
    private static final List<Integer> LARGURA_DE_NAO_LIDOS = List.of(40, 28, 90);

    // Quantas linhas ficam na memória antes de irem para o disco.
    private static final int JANELA_DE_LINHAS = 500;

    private static final int LARGURA_DE_UM_CARACTERE = 256;

    private static final List<String> CABECALHO_DE_ACHADOS = List.of(
            "Documento (pseudônimo)", "Modelo", "Série", "Número", "Emissão", "UF",
            "Item", "Regra", "Versão da regra", "Severidade",
            "Campo analisado", "Valor encontrado", "Valor esperado",
            "Fundamento normativo", "Vigência de", "Vigência até",
            "Valor em risco", "Tratativa", "Justificativa", "Tratado em");

    private static final List<Integer> LARGURA_DE_ACHADOS = List.of(
            22, 8, 8, 12, 12, 6, 6, 10, 14, 13, 26, 22, 22, 40, 12, 14, 14, 12, 40, 20);

    private static final List<String> CABECALHO_DE_NAO_AVALIADOS = List.of(
            "Documento (pseudônimo)", "Modelo", "Série", "Número", "Item",
            "Regra", "Versão da regra", "Motivo");

    private static final List<Integer> LARGURA_DE_NAO_AVALIADOS = List.of(
            22, 8, 8, 12, 6, 10, 14, 80);

    // Nome da propriedade que liga a justificativa na planilha. Acrescentada na Etapa 12.
    static final String PROPRIEDADE_EXPOR_JUSTIFICATIVA = "auditoria.exportacao.expor-justificativa";

    // Texto escrito no lugar da justificativa quando a instalação não a exporta.
    static final String JUSTIFICATIVA_OMITIDA =
            "(omitida) a justificativa não é exportada por esta instalação: é texto livre digitado por "
                    + "pessoa e pode conter CNPJ ou razão social. Ligue \"" + PROPRIEDADE_EXPOR_JUSTIFICATIVA
                    + "\" para incluí-la.";

    // Texto escrito em "Justificativa" e em "Tratado em" quando o apontamento não tem tratativa. Acrescentado em 04/10/2026 (D025): até essa data as duas células saíam em branco.
    static final String SEM_TRATATIVA =
            "(sem tratativa) ninguém registrou decisão sobre este apontamento nesta versão da regra";

    private final ZoneId fusoDeApresentacao;
    private final boolean exporJustificativa;

    // Construtor que usa o fuso do sistema.
    ExportadorXlsx() {
        // Fuso do sistema, para quem lê a planilha não ter de converter de UTC de cabeça.
        this(ZoneId.systemDefault());
    }

    // Construtor que recebe o fuso em que data e hora são mostradas. Desde a Etapa 12, a justificativa fica de fora.
    ExportadorXlsx(ZoneId fusoDeApresentacao) {
        this(fusoDeApresentacao, false);
    }

    // Construtor que o Spring usa: fuso do sistema e a justificativa conforme a configuração, desligada quando nada foi configurado. Acrescentado na Etapa 12.
    @Autowired
    ExportadorXlsx(@Value("${" + PROPRIEDADE_EXPOR_JUSTIFICATIVA + ":false}") boolean exporJustificativa) {
        this(ZoneId.systemDefault(), exporJustificativa);
    }

    // Construtor que recebe o fuso e se a justificativa vai para a planilha. Acrescentado na Etapa 12.
    ExportadorXlsx(ZoneId fusoDeApresentacao, boolean exporJustificativa) {
        this.fusoDeApresentacao = fusoDeApresentacao;
        this.exporJustificativa = exporJustificativa;
    }

    // Retorna a extensão do arquivo gravado.
    @Override
    public String extensao() {
        return "xlsx";
    }

    // Grava a planilha no destino, criando a pasta se ela não existir.
    @Override
    public void exportar(PapelDeTrabalho papel, Path destino) {
        if (papel == null) {
            throw new PapelDeTrabalhoInvalido("Não há papel de trabalho a exportar.");
        }
        if (destino == null) {
            throw new PapelDeTrabalhoInvalido("Não foi informado onde gravar a planilha.");
        }

        // O close() da escrita em fluxo já apaga os arquivos temporários.
        try (SXSSFWorkbook planilha = new SXSSFWorkbook(JANELA_DE_LINHAS)) {
            Celulas celulas = new Celulas(fusoDeApresentacao);
            EstilosDaPlanilha estilos = new EstilosDaPlanilha(planilha);

            Faixa faixa = Faixa.de(papel.natureza(), papel.execucao().versaoCatalogo(), estilos);
            escreverResumo(planilha, estilos, celulas, papel, faixa);
            escreverAchados(planilha, estilos, celulas, papel.achados(), faixa);
            escreverNaoAvaliados(planilha, estilos, papel.naoAvaliados(), faixa);
            escreverNaoLidos(planilha, estilos, papel.arquivosNaoLidos(), faixa);

            criarPastaDe(destino);
            try (OutputStream saida = Files.newOutputStream(destino)) {
                planilha.write(saida);
            }
        } catch (IOException erroDeEscrita) {
            throw new PapelDeTrabalhoInvalido(
                    "Não foi possível gravar o papel de trabalho em \"%s\".".formatted(destino),
                    erroDeEscrita);
        }
    }

    // Método auxiliar que escreve a aba Resumo: identificação, achados por gravidade e por regra, e os motivos de não avaliação.
    private void escreverResumo(
            SXSSFWorkbook planilha,
            EstilosDaPlanilha estilos,
            Celulas celulas,
            PapelDeTrabalho papel,
            Faixa faixa) {

        Sheet aba = planilha.createSheet(ABA_RESUMO);
        aba.setColumnWidth(0, 34 * LARGURA_DE_UM_CARACTERE);
        aba.setColumnWidth(1, 70 * LARGURA_DE_UM_CARACTERE);
        aba.setColumnWidth(2, 14 * LARGURA_DE_UM_CARACTERE);

        ExecucaoAuditoria execucao = papel.execucao();
        faixa.escrever(aba, 2);
        int proxima = 1;

        Celulas.texto(aba.createRow(proxima++), 0,
                "Papel de trabalho — auditoria de coerência de IBS/CBS", estilos.titulo());
        proxima++;

        Celulas.texto(aba.createRow(proxima++), 0, "Identificação da execução", estilos.titulo());
        proxima = par(aba, proxima, estilos, "Execução", execucao.id().toString());
        proxima = parComDataHora(aba, proxima, estilos, celulas, "Data e hora", execucao.dataHora());
        proxima = par(aba, proxima, estilos, "Versão do catálogo", execucao.versaoCatalogo());
        NaturezaDaCarga natureza = papel.natureza();
        proxima = par(aba, proxima, estilos, ROTULO_DA_NATUREZA, natureza.situacao().rotulo());
        // As duas listas só saem quando têm tabela: com catálogo normativo, nada na planilha fala em fictício.
        if (!natureza.tabelasFicticias().isEmpty()) {
            proxima = par(aba, proxima, estilos, ROTULO_DAS_FICTICIAS, String.join(", ", natureza.tabelasFicticias()));
        }
        if (!natureza.tabelasSemNaturezaDeclarada().isEmpty()) {
            proxima = par(aba, proxima, estilos, ROTULO_DAS_SEM_NATUREZA,
                    String.join(", ", natureza.tabelasSemNaturezaDeclarada()));
        }
        proxima = par(aba, proxima, estilos, "Versão do conjunto de regras",
                execucao.versaoConjuntoRegras());
        proxima = par(aba, proxima, estilos, ROTULO_DA_TOLERANCIA, papel.tolerancia()
                .map(ToleranciaDaExecucao::texto)
                .orElse("(" + ToleranciaDaExecucao.NAO_REGISTRADA + ")"));
        proxima = par(aba, proxima, estilos, "Resumo da entrada", execucao.hashEntrada());
        proxima = parComNumero(aba, proxima, estilos, "Documentos auditados",
                execucao.quantidadeDocumentos());
        proxima = parComNumero(aba, proxima, estilos, "Itens auditados", execucao.quantidadeItens());
        Optional<List<ArquivoIlegivel>> naoLidos = papel.arquivosNaoLidos();
        if (naoLidos.isPresent()) {
            proxima = parComNumero(aba, proxima, estilos, ROTULO_DOS_NAO_LIDOS, naoLidos.get().size());
        } else {
            proxima = par(aba, proxima, estilos, ROTULO_DOS_NAO_LIDOS, LEITURA_NAO_REGISTRADA);
        }
        Optional<Integer> repetidos = papel.documentosRepetidosDescartados();
        if (repetidos.isPresent()) {
            proxima = parComNumero(aba, proxima, estilos, ROTULO_DOS_REPETIDOS, repetidos.get());
        } else {
            proxima = par(aba, proxima, estilos, ROTULO_DOS_REPETIDOS, REPETIDOS_NAO_REGISTRADOS);
        }
        proxima++;

        Celulas.texto(aba.createRow(proxima++), 0, "Apontamentos por severidade", estilos.titulo());
        Row cabecalhoDeSeveridade = aba.createRow(proxima++);
        Celulas.texto(cabecalhoDeSeveridade, 0, "Severidade", estilos.cabecalho());
        Celulas.texto(cabecalhoDeSeveridade, 1, "Quantidade", estilos.cabecalho());
        for (Severidade severidade : execucao.severidadesContadas()) {
            Row linha = aba.createRow(proxima++);
            Celulas.texto(linha, 0, severidade.name(), estilos.texto());
            Celulas.inteiro(linha, 1, execucao.achadosDe(severidade), estilos.inteiro());
        }
        proxima = parComNumero(aba, proxima, estilos, "Total de apontamentos",
                execucao.quantidadeDeAchados());
        proxima++;

        Celulas.texto(aba.createRow(proxima++), 0, "Apontamentos por regra", estilos.titulo());
        Row cabecalhoDeRegra = aba.createRow(proxima++);
        Celulas.texto(cabecalhoDeRegra, 0, "Regra", estilos.cabecalho());
        Celulas.texto(cabecalhoDeRegra, 1, "Quantidade", estilos.cabecalho());
        // Ordena pelo código da regra, porque o mapa não garante ordem e duas planilhas da mesma execução precisam sair iguais.
        for (Map.Entry<String, Integer> porRegra : ordenadasPorRegra(execucao)) {
            Row linha = aba.createRow(proxima++);
            Celulas.texto(linha, 0, porRegra.getKey(), estilos.texto());
            Celulas.inteiro(linha, 1, porRegra.getValue(), estilos.inteiro());
        }
        proxima++;

        Celulas.texto(aba.createRow(proxima++), 0, "Não avaliados", estilos.titulo());
        Row explicacao = aba.createRow(proxima++);
        Celulas.texto(explicacao, 0,
                "Avaliação não concluída não é conformidade: a regra não teve como julgar, "
                        + "e o motivo diz o que faltou.", estilos.textoLongo());
        aba.addMergedRegion(new CellRangeAddress(explicacao.getRowNum(), explicacao.getRowNum(), 0, 2));

        proxima = parComNumero(aba, proxima, estilos, "Avaliações não concluídas",
                papel.quantidadeDeNaoAvaliados());
        proxima = parComNumero(aba, proxima, estilos, "Itens atingidos", papel.itensNaoAvaliados());
        proxima++;

        Row cabecalhoDeMotivo = aba.createRow(proxima++);
        Celulas.texto(cabecalhoDeMotivo, 0, "Regra", estilos.cabecalho());
        Celulas.texto(cabecalhoDeMotivo, 1, "Motivo", estilos.cabecalho());
        Celulas.texto(cabecalhoDeMotivo, 2, "Quantidade", estilos.cabecalho());
        for (MotivoAgrupado agrupado : papel.motivosAgrupados()) {
            Row linha = aba.createRow(proxima++);
            Celulas.texto(linha, 0, agrupado.regraId(), estilos.texto());
            Celulas.texto(linha, 1, agrupado.motivo(), estilos.textoLongo());
            Celulas.inteiro(linha, 2, agrupado.quantidade(), estilos.inteiro());
        }
    }

    // Método auxiliar que escreve a aba Achados, uma linha por apontamento.
    private void escreverAchados(
            SXSSFWorkbook planilha,
            EstilosDaPlanilha estilos,
            Celulas celulas,
            List<LinhaDeAchado> achados,
            Faixa faixa) {

        Sheet aba = criarAbaComCabecalho(
                planilha, estilos, ABA_ACHADOS, CABECALHO_DE_ACHADOS, LARGURA_DE_ACHADOS, faixa);

        int proxima = LINHA_DO_CABECALHO + 1;
        for (LinhaDeAchado achado : achados) {
            Row linha = aba.createRow(proxima++);
            int coluna = 0;

            Celulas.texto(linha, coluna++, achado.documentoPseudonimizado(), estilos.texto());
            Celulas.texto(linha, coluna++, achado.modelo(), estilos.texto());
            Celulas.texto(linha, coluna++, achado.serie(), estilos.texto());
            Celulas.texto(linha, coluna++, achado.numero(), estilos.texto());
            Celulas.data(linha, coluna++, achado.dataEmissao(), estilos.data());
            Celulas.texto(linha, coluna++, achado.ufEmitente().sigla(), estilos.texto());
            Celulas.inteiro(linha, coluna++, achado.numeroItem(), estilos.inteiro());
            Celulas.texto(linha, coluna++, achado.regraId(), estilos.texto());
            Celulas.texto(linha, coluna++, achado.regraVersao(), estilos.texto());
            Celulas.texto(linha, coluna++, achado.severidade().name(), estilos.texto());

            Celulas.texto(linha, coluna++, Celulas.juntar(achado.campos()), estilos.textoLongo());
            Celulas.texto(linha, coluna++,
                    Celulas.juntarOpcionais(achado.valoresEncontrados(), Celulas.NAO_INFORMADO),
                    estilos.textoLongo());
            Celulas.texto(linha, coluna++,
                    Celulas.juntarOpcionais(achado.valoresEsperados(), Celulas.SEM_REFERENCIA),
                    estilos.textoLongo());

            Celulas.texto(linha, coluna++, achado.fundamentoNormativo(), estilos.textoLongo());
            Celulas.data(linha, coluna++, achado.vigenciaInicio(), estilos.data());
            if (achado.vigenciaFim().isPresent()) {
                Celulas.data(linha, coluna++, achado.vigenciaFim().get(), estilos.data());
            } else {
                Celulas.texto(linha, coluna++, Celulas.SEM_FIM_DECLARADO, estilos.texto());
            }

            Celulas.valorEmRisco(linha, coluna++, achado.valorEmRisco(),
                    achado.motivoDoValorAusente(), estilos);

            Celulas.texto(linha, coluna++, achado.statusDeTratativa().name(), estilos.texto());
            // Etapa 12: sem a propriedade ligada, a justificativa dá lugar ao motivo da omissão.
            // Emenda de 04/10/2026 (D025): sem tratativa, a célula diz isso. Até essa data saía em branco.
            Celulas.texto(linha, coluna++,
                    achado.justificativaDaTratativa()
                            .map(justificativa -> exporJustificativa ? justificativa : JUSTIFICATIVA_OMITIDA)
                            .orElse(SEM_TRATATIVA),
                    estilos.textoLongo());
            if (achado.tratadoEm().isPresent()) {
                celulas.dataHora(linha, coluna, achado.tratadoEm().get(), estilos.dataHora());
            } else {
                // Emenda de 04/10/2026 (D025): sem tratativa, a ausência é escrita, como na justificativa. Até essa data a célula saía em branco.
                Celulas.texto(linha, coluna, SEM_TRATATIVA, estilos.textoLongo());
            }
        }
    }

    // Método auxiliar que escreve a aba Não avaliados, uma linha por avaliação não concluída.
    private void escreverNaoAvaliados(
            SXSSFWorkbook planilha, EstilosDaPlanilha estilos, List<LinhaNaoAvaliada> naoAvaliados, Faixa faixa) {

        Sheet aba = criarAbaComCabecalho(planilha, estilos, ABA_NAO_AVALIADOS,
                CABECALHO_DE_NAO_AVALIADOS, LARGURA_DE_NAO_AVALIADOS, faixa);

        int proxima = LINHA_DO_CABECALHO + 1;
        for (LinhaNaoAvaliada naoAvaliada : naoAvaliados) {
            Row linha = aba.createRow(proxima++);
            int coluna = 0;

            Celulas.texto(linha, coluna++, naoAvaliada.documentoPseudonimizado(), estilos.texto());
            Celulas.texto(linha, coluna++, naoAvaliada.modelo(), estilos.texto());
            Celulas.texto(linha, coluna++, naoAvaliada.serie(), estilos.texto());
            Celulas.texto(linha, coluna++, naoAvaliada.numero(), estilos.texto());
            Celulas.inteiro(linha, coluna++, naoAvaliada.numeroItem(), estilos.inteiro());
            Celulas.texto(linha, coluna++, naoAvaliada.regraId(), estilos.texto());
            Celulas.texto(linha, coluna++, naoAvaliada.regraVersao(), estilos.texto());
            Celulas.texto(linha, coluna, naoAvaliada.motivo(), estilos.textoLongo());
        }
    }

    // Método auxiliar que escreve a aba Não lidos, uma linha por arquivo; sem arquivo, ou sem a leitura registrada, uma linha dizendo qual dos dois.
    private void escreverNaoLidos(
            SXSSFWorkbook planilha, EstilosDaPlanilha estilos, Optional<List<ArquivoIlegivel>> naoLidos,
            Faixa faixa) {

        Sheet aba = criarAbaComCabecalho(planilha, estilos, ABA_NAO_LIDOS,
                CABECALHO_DE_NAO_LIDOS, LARGURA_DE_NAO_LIDOS, faixa);

        int primeira = LINHA_DO_CABECALHO + 1;
        if (naoLidos.isEmpty() || naoLidos.get().isEmpty()) {
            Row linha = aba.createRow(primeira);
            Celulas.texto(linha, 0, naoLidos.isEmpty() ? LEITURA_NAO_REGISTRADA : NENHUM_NAO_LIDO,
                    estilos.textoLongo());
            aba.addMergedRegion(new CellRangeAddress(primeira, primeira, 0, 2));
            return;
        }
        int proxima = primeira;
        for (ArquivoIlegivel arquivo : naoLidos.get()) {
            Row linha = aba.createRow(proxima++);
            Celulas.texto(linha, 0, arquivo.origem(), estilos.texto());
            Celulas.texto(linha, 1, arquivo.tipoDeErro(), estilos.texto());
            Celulas.texto(linha, 2, arquivo.motivo(), estilos.textoLongo());
        }
    }

    // Método auxiliar que ordena as contagens por regra, do código menor para o maior.
    private static List<Map.Entry<String, Integer>> ordenadasPorRegra(ExecucaoAuditoria execucao) {
        return execucao.achadosPorRegra().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    // Método auxiliar que cria a aba com a faixa na primeira linha, o cabeçalho na segunda, as larguras das colunas e as duas linhas fixas.
    private static Sheet criarAbaComCabecalho(
            SXSSFWorkbook planilha,
            EstilosDaPlanilha estilos,
            String nome,
            List<String> cabecalho,
            List<Integer> larguras,
            Faixa faixa) {

        Sheet aba = planilha.createSheet(nome);
        faixa.escrever(aba, cabecalho.size() - 1);
        Row linha = aba.createRow(LINHA_DO_CABECALHO);
        for (int coluna = 0; coluna < cabecalho.size(); coluna++) {
            Celulas.texto(linha, coluna, cabecalho.get(coluna), estilos.cabecalho());
            aba.setColumnWidth(coluna, larguras.get(coluna) * LARGURA_DE_UM_CARACTERE);
        }
        // Faixa e cabeçalho fixos: quem rola uma planilha grande não perde nem o nome das colunas nem o aviso.
        aba.createFreezePane(0, LINHA_DO_CABECALHO + 1);
        return aba;
    }

    // Representa a faixa de procedência do catálogo (D021): o texto e o estilo, iguais em todas as abas.
    record Faixa(String texto, org.apache.poi.ss.usermodel.CellStyle estilo) {

        // Método estático que monta a faixa a partir da natureza da carga. O texto diz a situação por extenso, a explicação, as tabelas fictícias e as sem natureza declarada, e a versão da carga.
        static Faixa de(NaturezaDaCarga natureza, String versaoDoCatalogo, EstilosDaPlanilha estilos) {
            SituacaoDaNatureza situacao = natureza.situacao();
            StringBuilder texto = new StringBuilder(situacao.rotulo().toUpperCase(Locale.ROOT))
                    .append(" — ").append(situacao.explicacao());
            if (!natureza.tabelasFicticias().isEmpty()) {
                texto.append(' ').append(ROTULO_DAS_FICTICIAS).append(": ")
                        .append(String.join(", ", natureza.tabelasFicticias())).append('.');
            }
            if (!natureza.tabelasSemNaturezaDeclarada().isEmpty()) {
                texto.append(' ').append(ROTULO_DAS_SEM_NATUREZA).append(": ")
                        .append(String.join(", ", natureza.tabelasSemNaturezaDeclarada())).append('.');
            }
            texto.append(" Carga de catálogo: ").append(versaoDoCatalogo).append('.');
            return new Faixa(texto.toString(),
                    situacao.exigeAviso() ? estilos.faixaDeAviso() : estilos.faixaNormativa());
        }

        // Escreve a faixa na primeira linha da aba, mesclada até a última coluna.
        void escrever(Sheet aba, int ultimaColuna) {
            Row linha = aba.createRow(0);
            linha.setHeightInPoints(48);
            for (int coluna = 0; coluna <= ultimaColuna; coluna++) {
                Celulas.texto(linha, coluna, coluna == 0 ? texto : "", estilo);
            }
            if (ultimaColuna > 0) {
                aba.addMergedRegion(new CellRangeAddress(0, 0, 0, ultimaColuna));
            }
        }
    }

    // Método auxiliar que escreve um par rótulo e texto numa linha.
    private static int par(Sheet aba, int numeroDaLinha, EstilosDaPlanilha estilos,
                           String rotulo, String valor) {
        Row linha = aba.createRow(numeroDaLinha);
        Celulas.texto(linha, 0, rotulo, estilos.rotulo());
        Celulas.texto(linha, 1, valor, estilos.texto());
        return numeroDaLinha + 1;
    }

    // Método auxiliar que escreve um par rótulo e número numa linha.
    private static int parComNumero(Sheet aba, int numeroDaLinha, EstilosDaPlanilha estilos,
                                    String rotulo, long valor) {
        Row linha = aba.createRow(numeroDaLinha);
        Celulas.texto(linha, 0, rotulo, estilos.rotulo());
        Celulas.inteiro(linha, 1, valor, estilos.inteiro());
        return numeroDaLinha + 1;
    }

    // Método auxiliar que escreve um par rótulo e data e hora numa linha.
    private static int parComDataHora(Sheet aba, int numeroDaLinha, EstilosDaPlanilha estilos,
                                      Celulas celulas, String rotulo, java.time.Instant valor) {
        Row linha = aba.createRow(numeroDaLinha);
        Celulas.texto(linha, 0, rotulo, estilos.rotulo());
        celulas.dataHora(linha, 1, valor, estilos.dataHora());
        return numeroDaLinha + 1;
    }

    // Método auxiliar que cria a pasta de destino, se ela não existir.
    private static void criarPastaDe(Path destino) throws IOException {
        Path pasta = destino.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
    }
}
