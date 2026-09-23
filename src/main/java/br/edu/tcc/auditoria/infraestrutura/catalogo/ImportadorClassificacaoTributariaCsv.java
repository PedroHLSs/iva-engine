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

// Classe que importa as classificações tributárias de um CSV. Desde 14/09/2026, dispositivoLegal, redução e fonteNormativa também podem vir por tributo (_cbs e _ibs): texto igual vira um só, texto diferente vira "CBS: … | IBS: …", e redução diferente recusa a linha, porque escolher uma seria decidir sobre a norma.
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

    // Representa as duas formas de escrever um campo no cabeçalho: a coluna única ou o par por tributo.
    private record Campo(String colunaUnica, String colunaCbs, String colunaIbs) {
    }

    // Os três campos que aceitam a forma por tributo.
    private static final Campo DISPOSITIVO_LEGAL = new Campo(
            COLUNA_DISPOSITIVO_LEGAL, COLUNA_DISPOSITIVO_LEGAL_CBS, COLUNA_DISPOSITIVO_LEGAL_IBS);
    private static final Campo REDUCAO = new Campo(
            COLUNA_PERCENTUAL_REDUCAO, COLUNA_REDUCAO_CBS, COLUNA_REDUCAO_IBS);
    private static final Campo FONTE_NORMATIVA = new Campo(
            ProcedenciaEmCsv.COLUNA_FONTE_NORMATIVA, COLUNA_FONTE_NORMATIVA_CBS, COLUNA_FONTE_NORMATIVA_IBS);

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista.
    public TabelaImportada<ClassificacaoTributaria> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<ClassificacaoTributaria> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV numa classificação; CSTs e campos obrigatórios vêm numa célula só, separados por |.
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

    // Método auxiliar que lê um campo de texto da coluna única ou do par; se os dois lados forem diferentes, guarda os dois com o nome do tributo.
    private static String textoObrigatorio(LinhaCsv linha, Campo campo) {
        if (!estaPorTributo(linha, campo)) {
            return linha.textoObrigatorio(campo.colunaUnica());
        }
        String cbs = linha.textoObrigatorio(campo.colunaCbs());
        String ibs = linha.textoObrigatorio(campo.colunaIbs());
        return cbs.equals(ibs) ? cbs : "CBS: %s | IBS: %s".formatted(cbs, ibs);
    }

    // Método auxiliar que lê a redução da coluna única ou do par; recusa a linha se os dois lados forem diferentes.
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

    // Método auxiliar que diz se o campo veio por tributo; recusa as duas formas juntas e o par pela metade.
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

    // Método auxiliar que devolve o valor como foi escrito, entre aspas, ou "em branco", para a mensagem de erro.
    private static String comoEscrito(LinhaCsv linha, String coluna) {
        return linha.texto(coluna).map(valor -> "\"" + valor + "\"").orElse("em branco");
    }
}
