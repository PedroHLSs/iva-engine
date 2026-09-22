package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Importa classificações tributárias de um CSV.
 *
 * <p>Colunas esperadas: {@code codigo}, {@code cstsCompativeis},
 * {@code dispositivoLegal}, {@code indicadorDeBeneficio},
 * {@code percentualReducao}, {@code camposObrigatoriosCondicionados}, mais as
 * três comuns a todo catálogo — {@code vigenciaInicio}, {@code vigenciaFim} e
 * {@code fonteNormativa}.</p>
 *
 * <p>{@code cstsCompativeis} e {@code camposObrigatoriosCondicionados} são
 * listas dentro de um único campo, separadas por {@code |}.</p>
 *
 * <p><b>Emenda de 14/09/2026, sobre a Etapa 2.</b> Até esta data, as colunas
 * acima eram a única forma aceita. Passou a existir uma segunda, por tributo,
 * para três campos: {@code dispositivoLegal_cbs} e {@code dispositivoLegal_ibs},
 * {@code reducao_cbs} e {@code reducao_ibs}, {@code fonteNormativa_cbs} e
 * {@code fonteNormativa_ibs}. A escolha é campo a campo, e o cabeçalho não pode
 * trazer as duas formas para o mesmo campo nem só metade do par.</p>
 *
 * <p>A classificação continua guardando um valor só por campo — o domínio não
 * mudou —, e por isso o par é juntado aqui, <b>sem escolher entre os dois</b>:</p>
 *
 * <ul>
 *   <li>valores iguais viram um valor só;</li>
 *   <li>texto diferente é guardado inteiro, rotulado:
 *       {@code CBS: … | IBS: …};</li>
 *   <li>redução diferente recusa a linha. Número não se rotula, e escolher um
 *       dos dois seria decidir sobre a norma. A comparação é do valor escrito,
 *       casas decimais incluídas, e só vírgula e ponto se equivalem, como no
 *       resto do CSV.</li>
 * </ul>
 *
 * <p>Célula em branco num dos lados é recusada como seria na coluna única.
 * Redução em branco nos dois lados continua sendo redução não declarada, que é
 * diferente de redução zero.</p>
 */
public final class ImportadorClassificacaoTributariaCsv {

    public static final String COLUNA_CODIGO = "codigo";
    public static final String COLUNA_CSTS_COMPATIVEIS = "cstsCompativeis";
    public static final String COLUNA_DISPOSITIVO_LEGAL = "dispositivoLegal";
    public static final String COLUNA_INDICADOR_DE_BENEFICIO = "indicadorDeBeneficio";
    public static final String COLUNA_PERCENTUAL_REDUCAO = "percentualReducao";
    public static final String COLUNA_CAMPOS_OBRIGATORIOS = "camposObrigatoriosCondicionados";

    public static final String COLUNA_DISPOSITIVO_LEGAL_CBS = "dispositivoLegal_cbs";
    public static final String COLUNA_DISPOSITIVO_LEGAL_IBS = "dispositivoLegal_ibs";
    public static final String COLUNA_REDUCAO_CBS = "reducao_cbs";
    public static final String COLUNA_REDUCAO_IBS = "reducao_ibs";
    public static final String COLUNA_FONTE_NORMATIVA_CBS = "fonteNormativa_cbs";
    public static final String COLUNA_FONTE_NORMATIVA_IBS = "fonteNormativa_ibs";

    /** As duas formas de escrever um mesmo campo no cabeçalho. */
    private record Campo(String colunaUnica, String colunaCbs, String colunaIbs) {
    }

    private static final Campo DISPOSITIVO_LEGAL = new Campo(
            COLUNA_DISPOSITIVO_LEGAL, COLUNA_DISPOSITIVO_LEGAL_CBS, COLUNA_DISPOSITIVO_LEGAL_IBS);
    private static final Campo REDUCAO = new Campo(
            COLUNA_PERCENTUAL_REDUCAO, COLUNA_REDUCAO_CBS, COLUNA_REDUCAO_IBS);
    private static final Campo FONTE_NORMATIVA = new Campo(
            ProcedenciaEmCsv.COLUNA_FONTE_NORMATIVA, COLUNA_FONTE_NORMATIVA_CBS, COLUNA_FONTE_NORMATIVA_IBS);

    /*
     * Emenda da etapa de conferência, sobre a Etapa 2.
     *
     * O retorno passou de List para TabelaImportada porque a procedência das
     * linhas é fato sobre elas, e precisa sair pelo mesmo caminho. Devolvê-la
     * à parte permitiria ler os registros de um arquivo e a natureza de outro.
     */
    public TabelaImportada<ClassificacaoTributaria> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    public TabelaImportada<ClassificacaoTributaria> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    private ClassificacaoTributaria converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(
                linha, lida -> textoObrigatorio(lida, FONTE_NORMATIVA));

        String codigo = linha.textoObrigatorio(COLUNA_CODIGO);
        List<String> csts = linha.lista(COLUNA_CSTS_COMPATIVEIS);
        String dispositivoLegal = textoObrigatorio(linha, DISPOSITIVO_LEGAL);
        boolean indicadorDeBeneficio = linha.booleanoObrigatorio(COLUNA_INDICADOR_DE_BENEFICIO);
        List<String> camposObrigatorios = linha.lista(COLUNA_CAMPOS_OBRIGATORIOS);

        return linha.converterCom(() -> {
            Set<CodigoCst> cstsCompativeis = new LinkedHashSet<>();
            csts.forEach(cst -> cstsCompativeis.add(new CodigoCst(cst)));

            return new ClassificacaoTributaria(
                    new CodigoClassificacaoTributaria(codigo),
                    cstsCompativeis,
                    dispositivoLegal,
                    indicadorDeBeneficio,
                    reducao(linha),
                    camposObrigatorios,
                    procedencia);
        });
    }

    private static String textoObrigatorio(LinhaCsv linha, Campo campo) {
        if (!estaPorTributo(linha, campo)) {
            return linha.textoObrigatorio(campo.colunaUnica());
        }
        String cbs = linha.textoObrigatorio(campo.colunaCbs());
        String ibs = linha.textoObrigatorio(campo.colunaIbs());
        return cbs.equals(ibs) ? cbs : "CBS: %s | IBS: %s".formatted(cbs, ibs);
    }

    private static Optional<BigDecimal> reducao(LinhaCsv linha) {
        if (!estaPorTributo(linha, REDUCAO)) {
            return linha.decimal(REDUCAO.colunaUnica());
        }
        Optional<BigDecimal> cbs = linha.decimal(REDUCAO.colunaCbs());
        Optional<BigDecimal> ibs = linha.decimal(REDUCAO.colunaIbs());
        if (!cbs.equals(ibs)) {
            throw new ImportacaoDeCatalogoInvalida(
                    ("Linha %d: \"%s\" (%s) e \"%s\" (%s) não coincidem, e a classificação guarda um "
                            + "percentual de redução só. O importador não escolhe entre os dois: informe o "
                            + "mesmo valor nas duas colunas, escrito com as mesmas casas decimais.")
                            .formatted(linha.numero(),
                                    REDUCAO.colunaCbs(), comoEscrito(linha, REDUCAO.colunaCbs()),
                                    REDUCAO.colunaIbs(), comoEscrito(linha, REDUCAO.colunaIbs())));
        }
        return cbs;
    }

    /** Se o cabeçalho escreve o campo por tributo; recusa as duas formas juntas e o par pela metade. */
    private static boolean estaPorTributo(LinhaCsv linha, Campo campo) {
        Set<String> colunas = linha.valores().keySet();
        boolean temUnica = colunas.contains(campo.colunaUnica());
        boolean temCbs = colunas.contains(campo.colunaCbs());
        boolean temIbs = colunas.contains(campo.colunaIbs());

        if (temUnica && (temCbs || temIbs)) {
            throw new ImportacaoDeCatalogoInvalida(
                    ("Linha %d: o cabeçalho traz \"%s\" e também a forma por tributo (\"%s\", \"%s\"). "
                            + "Use uma forma só para o campo: com as duas, não há como saber qual vale.")
                            .formatted(linha.numero(), campo.colunaUnica(), campo.colunaCbs(), campo.colunaIbs()));
        }
        if (temCbs != temIbs) {
            throw new ImportacaoDeCatalogoInvalida(
                    "Linha %d: o cabeçalho traz \"%s\" sem \"%s\". A forma por tributo exige as duas colunas."
                            .formatted(linha.numero(),
                                    temCbs ? campo.colunaCbs() : campo.colunaIbs(),
                                    temCbs ? campo.colunaIbs() : campo.colunaCbs()));
        }
        return temCbs;
    }

    private static String comoEscrito(LinhaCsv linha, String coluna) {
        return linha.texto(coluna).map(valor -> "\"" + valor + "\"").orElse("em branco");
    }
}
