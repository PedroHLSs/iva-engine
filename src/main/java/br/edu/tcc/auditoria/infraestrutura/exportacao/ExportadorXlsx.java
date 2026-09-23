package br.edu.tcc.auditoria.infraestrutura.exportacao;

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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

// Classe que grava o papel de trabalho em xlsx, com três abas: Resumo, que começa pela identificação da execução, Achados e Não avaliados. Escreve em fluxo, com poucas linhas na memória, por isso as larguras das colunas são fixas.
@Component
class ExportadorXlsx implements ExportadorDePapelDeTrabalho {

    static final String ABA_RESUMO = "Resumo";
    static final String ABA_ACHADOS = "Achados";
    static final String ABA_NAO_AVALIADOS = "Não avaliados";

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

    private final ZoneId fusoDeApresentacao;

    // Construtor que usa o fuso do sistema.
    ExportadorXlsx() {
        // Fuso do sistema, para quem lê a planilha não ter de converter de UTC de cabeça.
        this(ZoneId.systemDefault());
    }

    // Construtor que recebe o fuso em que data e hora são mostradas.
    ExportadorXlsx(ZoneId fusoDeApresentacao) {
        this.fusoDeApresentacao = fusoDeApresentacao;
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

            escreverResumo(planilha, estilos, celulas, papel);
            escreverAchados(planilha, estilos, celulas, papel.achados());
            escreverNaoAvaliados(planilha, estilos, papel.naoAvaliados());

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
            PapelDeTrabalho papel) {

        Sheet aba = planilha.createSheet(ABA_RESUMO);
        aba.setColumnWidth(0, 34 * LARGURA_DE_UM_CARACTERE);
        aba.setColumnWidth(1, 70 * LARGURA_DE_UM_CARACTERE);
        aba.setColumnWidth(2, 14 * LARGURA_DE_UM_CARACTERE);

        ExecucaoAuditoria execucao = papel.execucao();
        int proxima = 0;

        Celulas.texto(aba.createRow(proxima++), 0,
                "Papel de trabalho — auditoria de coerência de IBS/CBS", estilos.titulo());
        proxima++;

        Celulas.texto(aba.createRow(proxima++), 0, "Identificação da execução", estilos.titulo());
        proxima = par(aba, proxima, estilos, "Execução", execucao.id().toString());
        proxima = parComDataHora(aba, proxima, estilos, celulas, "Data e hora", execucao.dataHora());
        proxima = par(aba, proxima, estilos, "Versão do catálogo", execucao.versaoCatalogo());
        proxima = par(aba, proxima, estilos, "Versão do conjunto de regras",
                execucao.versaoConjuntoRegras());
        proxima = par(aba, proxima, estilos, "Resumo da entrada", execucao.hashEntrada());
        proxima = parComNumero(aba, proxima, estilos, "Documentos auditados",
                execucao.quantidadeDocumentos());
        proxima = parComNumero(aba, proxima, estilos, "Itens auditados", execucao.quantidadeItens());
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
            List<LinhaDeAchado> achados) {

        Sheet aba = criarAbaComCabecalho(
                planilha, estilos, ABA_ACHADOS, CABECALHO_DE_ACHADOS, LARGURA_DE_ACHADOS);

        int proxima = 1;
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
                    achado.motivoDoValorAusente(), estilos.monetario(), estilos.textoLongo());

            Celulas.texto(linha, coluna++, achado.statusDeTratativa().name(), estilos.texto());
            Celulas.texto(linha, coluna++,
                    achado.justificativaDaTratativa().orElse(""), estilos.textoLongo());
            if (achado.tratadoEm().isPresent()) {
                celulas.dataHora(linha, coluna, achado.tratadoEm().get(), estilos.dataHora());
            } else {
                Celulas.texto(linha, coluna, "", estilos.texto());
            }
        }
    }

    // Método auxiliar que escreve a aba Não avaliados, uma linha por avaliação não concluída.
    private void escreverNaoAvaliados(
            SXSSFWorkbook planilha, EstilosDaPlanilha estilos, List<LinhaNaoAvaliada> naoAvaliados) {

        Sheet aba = criarAbaComCabecalho(planilha, estilos, ABA_NAO_AVALIADOS,
                CABECALHO_DE_NAO_AVALIADOS, LARGURA_DE_NAO_AVALIADOS);

        int proxima = 1;
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

    // Método auxiliar que ordena as contagens por regra, do código menor para o maior.
    private static List<Map.Entry<String, Integer>> ordenadasPorRegra(ExecucaoAuditoria execucao) {
        return execucao.achadosPorRegra().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    // Método auxiliar que cria a aba com o cabeçalho, as larguras das colunas e o cabeçalho fixo.
    private static Sheet criarAbaComCabecalho(
            SXSSFWorkbook planilha,
            EstilosDaPlanilha estilos,
            String nome,
            List<String> cabecalho,
            List<Integer> larguras) {

        Sheet aba = planilha.createSheet(nome);
        Row linha = aba.createRow(0);
        for (int coluna = 0; coluna < cabecalho.size(); coluna++) {
            Celulas.texto(linha, coluna, cabecalho.get(coluna), estilos.cabecalho());
            aba.setColumnWidth(coluna, larguras.get(coluna) * LARGURA_DE_UM_CARACTERE);
        }
        // Cabeçalho fixo, para quem confere uma planilha grande não perder o nome das colunas.
        aba.createFreezePane(0, 1);
        return aba;
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
