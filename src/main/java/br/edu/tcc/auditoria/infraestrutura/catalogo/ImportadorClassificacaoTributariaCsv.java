package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.IncidenciaDaReducao;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Classe que importa as classificações tributárias de um CSV. Desde 14/09/2026, dispositivoLegal, redução e fonteNormativa também podem vir por tributo (_cbs e _ibs): texto igual vira um só, texto diferente vira "CBS: … | IBS: …", e redução diferente recusa a linha, porque escolher uma seria decidir sobre a norma. Desde 30/09/2026 lê também a coluna opcional tributacaoIntegral (S ou N), que a R04 1.1.0 usa, e a coluna opcional anexosAdmitidos (identificadores separados por | ou NENHUM), que a R03 1.1.0 usa.
// Emenda de 03/10/2026 (D015): camposObrigatoriosCondicionados passou a ter três estados, como anexosAdmitidos — célula em branco é "não declarado", NENHUM declara que o código não exige campo, e os nomes vêm separados por |. Até essa data a célula em branco virava lista vazia, e a R07 lia lista vazia como "nenhum campo exigido".
// Emenda de 03/10/2026 (D017): lê também a coluna opcional reducaoIncideSobre (ALIQUOTA ou BASE), que a R05 1.3.0 usa no lugar dos CST que trazia escritos em código.
public final class ImportadorClassificacaoTributariaCsv {

    public static final String COLUNA_CODIGO = "codigo";
    public static final String COLUNA_CSTS_COMPATIVEIS = "cstsCompativeis";
    public static final String COLUNA_DISPOSITIVO_LEGAL = "dispositivoLegal";
    public static final String COLUNA_INDICADOR_DE_BENEFICIO = "indicadorDeBeneficio";
    public static final String COLUNA_PERCENTUAL_REDUCAO = "percentualReducao";
    public static final String COLUNA_CAMPOS_OBRIGATORIOS = "camposObrigatoriosCondicionados";
    public static final String COLUNA_TRIBUTACAO_INTEGRAL = "tributacaoIntegral";
    public static final String COLUNA_ANEXOS_ADMITIDOS = "anexosAdmitidos";
    public static final String COLUNA_REDUCAO_INCIDE_SOBRE = "reducaoIncideSobre";

    // Valor da coluna anexosAdmitidos que declara que o código não exige anexo.
    public static final String VALOR_NENHUM = "NENHUM";

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

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista. Mudou na Etapa 12: antes parava na primeira linha recusada; agora lê o arquivo inteiro e recusa com todas as linhas problemáticas numa mensagem só.
    public TabelaImportada<ClassificacaoTributaria> importar(Reader origem) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();
        TabelaImportada<ClassificacaoTributaria> tabela =
                importar(origem, LeitorDeCatalogoEmCsv.ARQUIVO_CLASSIFICACAO_TRIBUTARIA, recusas);
        recusas.lancarSeHouver();
        return tabela;
    }

    // Lê o CSV registrando cada linha recusada em vez de parar na primeira, para a carga ser recusada com todas de uma vez. Acrescentado na Etapa 12.
    TabelaImportada<ClassificacaoTributaria> importar(Reader origem, String arquivo, RecusasDaCarga recusas)
            throws IOException {
        return LeituraDeTabela.ler(origem, arquivo, this::converter, recusas);
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<ClassificacaoTributaria> importar(Path arquivo) throws IOException {
        // Emenda de 04/10/2026 (D024): abre pelo ponto único, em UTF-8 estrito, como a pasta e o envio.
        try (Reader origem = AberturaEmUtf8.abrir(arquivo, RecusaDoCatalogoEmCsv.INSTANCIA)) {
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
        Optional<List<String>> camposObrigatorios = camposObrigatorios(linha);

        // Etapa 12: código e CSTs são convertidos à parte, para a recusa dizer a coluna e o valor.
        CodigoClassificacaoTributaria codigoValido = linha.converterCom(
                COLUNA_CODIGO, () -> new CodigoClassificacaoTributaria(codigo));
        Set<CodigoCst> cstsValidos = linha.converterCom(COLUNA_CSTS_COMPATIVEIS, () -> {
            Set<CodigoCst> convertidos = new LinkedHashSet<>();
            csts.forEach(cst -> convertidos.add(new CodigoCst(cst)));
            return convertidos;
        });
        Optional<BigDecimal> reducao = reducao(linha);
        Optional<Boolean> tributacaoIntegral = tributacaoIntegral(linha, indicadorDeBeneficio, reducao);
        Optional<Set<IdentificadorAnexo>> anexosAdmitidos = anexosAdmitidos(linha);
        Optional<IncidenciaDaReducao> reducaoIncideSobre = reducaoIncideSobre(linha);

        return linha.converterCom(() -> {
            Set<CodigoCst> cstsCompativeis = cstsValidos;

            return new ClassificacaoTributaria(
                    codigoValido,
                    cstsCompativeis,
                    dispositivoLegal,
                    indicadorDeBeneficio,
                    reducao,
                    reducaoIncideSobre,
                    tributacaoIntegral,
                    anexosAdmitidos,
                    camposObrigatorios,
                    procedencia);
        });
    }

    // Método auxiliar que lê um campo de texto da coluna única ou do par; se os dois lados forem diferentes, guarda os dois com o nome do tributo.
    // Emenda de 04/10/2026 (D022): só lê dispositivo legal e fonte normativa, e recusa cada lado sem nenhuma letra, como "0", antes de juntar os dois. Até essa data "0" no lado do IBS entrava como "CBS: … | IBS: 0".
    private static String textoObrigatorio(LinhaCsv linha, Campo campo) {
        if (!estaPorTributo(linha, campo)) {
            return TextoQueIdentificaNorma.exigir(
                    linha, campo.colunaUnica(), linha.textoObrigatorio(campo.colunaUnica()));
        }
        String cbs = TextoQueIdentificaNorma.exigir(
                linha, campo.colunaCbs(), linha.textoObrigatorio(campo.colunaCbs()));
        String ibs = TextoQueIdentificaNorma.exigir(
                linha, campo.colunaIbs(), linha.textoObrigatorio(campo.colunaIbs()));
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
            throw new RecusaDeCampo(linha.numero(), REDUCAO.colunaIbs(),
                    linha.valorComoVeio(REDUCAO.colunaIbs()),
                    ("Linha %d: \"%s\" (%s) e \"%s\" (%s) não coincidem, e a classificação guarda um "
                            + "percentual de redução só. O importador não escolhe entre os dois: informe o "
                            + "mesmo valor nas duas colunas, escrito com as mesmas casas decimais.")
                            .formatted(linha.numero(),
                                    REDUCAO.colunaCbs(), comoEscrito(linha, REDUCAO.colunaCbs()),
                                    REDUCAO.colunaIbs(), comoEscrito(linha, REDUCAO.colunaIbs())),
                    null);
        }
        return cbs;
    }

    // Método auxiliar que lê a tributação integral: coluna ausente ou célula em branco é "não declarado"; só aceita S ou N, e recusa S junto de benefício ou de redução diferente de zero, que a decisão do usuário de 30/09/2026 declara incoerentes.
    private static Optional<Boolean> tributacaoIntegral(
            LinhaCsv linha, boolean indicadorDeBeneficio, Optional<BigDecimal> reducao) {
        if (!linha.valores().containsKey(COLUNA_TRIBUTACAO_INTEGRAL)) {
            return Optional.empty();
        }
        Optional<String> valor = linha.texto(COLUNA_TRIBUTACAO_INTEGRAL);
        if (valor.isEmpty()) {
            return Optional.empty();
        }
        boolean integral = switch (valor.get()) {
            case "S" -> true;
            case "N" -> false;
            default -> throw recusarIntegral(linha,
                    "Linha %d: a coluna \"%s\" aceita apenas \"S\" ou \"N\", ou fica em branco, mas veio \"%s\"."
                            .formatted(linha.numero(), COLUNA_TRIBUTACAO_INTEGRAL, valor.get()));
        };
        if (integral && indicadorDeBeneficio) {
            throw recusarIntegral(linha,
                    ("Linha %d: \"%s\" é S, mas \"%s\" é true. Tributação integral com marca de benefício "
                            + "é incoerente, e o importador não escolhe qual das duas vale.")
                            .formatted(linha.numero(), COLUNA_TRIBUTACAO_INTEGRAL, COLUNA_INDICADOR_DE_BENEFICIO));
        }
        if (integral && reducao.isPresent() && reducao.get().signum() != 0) {
            String colunaDaReducao = estaPorTributo(linha, REDUCAO) ? REDUCAO.colunaCbs() : REDUCAO.colunaUnica();
            throw recusarIntegral(linha,
                    ("Linha %d: \"%s\" é S, mas a redução declarada é %s. Tributação integral com redução "
                            + "diferente de zero é incoerente, e o importador não escolhe qual das duas vale.")
                            .formatted(linha.numero(), COLUNA_TRIBUTACAO_INTEGRAL,
                                    comoEscrito(linha, colunaDaReducao)));
        }
        return Optional.of(integral);
    }

    // Método auxiliar que lê os anexos admitidos: coluna ausente ou célula em branco é "não declarado"; NENHUM é o conjunto vazio; recusa NENHUM misturado com anexo, identificador vazio ou com espaço em volta, e identificador repetido. Não confere se o anexo existe em item-anexo.csv: este importador só vê o próprio arquivo.
    private static Optional<Set<IdentificadorAnexo>> anexosAdmitidos(LinhaCsv linha) {
        if (!linha.valores().containsKey(COLUNA_ANEXOS_ADMITIDOS)) {
            return Optional.empty();
        }
        Optional<String> valor = linha.texto(COLUNA_ANEXOS_ADMITIDOS);
        if (valor.isEmpty()) {
            return Optional.empty();
        }
        if (valor.get().equals(VALOR_NENHUM)) {
            return Optional.of(Set.of());
        }
        Set<IdentificadorAnexo> anexos = new LinkedHashSet<>();
        for (String parte : valor.get().split("\\|", -1)) {
            if (parte.equals(VALOR_NENHUM)) {
                throw recusarAnexos(linha,
                        "Linha %d: a coluna \"%s\" traz %s junto de anexo; %s vale sozinho."
                                .formatted(linha.numero(), COLUNA_ANEXOS_ADMITIDOS, VALOR_NENHUM, VALOR_NENHUM));
            }
            if (parte.isBlank() || !parte.equals(parte.strip())) {
                throw recusarAnexos(linha,
                        ("Linha %d: a coluna \"%s\" tem identificador vazio ou com espaço em volta; use os "
                                + "identificadores de item-anexo.csv separados por |, ou %s.")
                                .formatted(linha.numero(), COLUNA_ANEXOS_ADMITIDOS, VALOR_NENHUM));
            }
            if (!anexos.add(new IdentificadorAnexo(parte))) {
                throw recusarAnexos(linha,
                        "Linha %d: a coluna \"%s\" repete o identificador \"%s\"."
                                .formatted(linha.numero(), COLUNA_ANEXOS_ADMITIDOS, parte));
            }
        }
        return Optional.of(anexos);
    }

    // Método auxiliar que lê os campos exigidos (D015, 03/10/2026): coluna ausente é recusada, como sempre foi; célula em branco é "não declarado"; NENHUM é a lista vazia, declarada; os demais valores são nomes separados por |. Recusa NENHUM misturado com nome, nome vazio ou com espaço em volta, e nome repetido. Até 03/10/2026 a coluna passava por LinhaCsv.lista, que fazia da célula em branco uma lista vazia e descartava elemento vazio em silêncio.
    private static Optional<List<String>> camposObrigatorios(LinhaCsv linha) {
        Optional<String> valor = linha.texto(COLUNA_CAMPOS_OBRIGATORIOS);
        if (valor.isEmpty()) {
            return Optional.empty();
        }
        if (valor.get().equals(VALOR_NENHUM)) {
            return Optional.of(List.of());
        }
        List<String> campos = new ArrayList<>();
        for (String parte : valor.get().split("\\|", -1)) {
            if (parte.equals(VALOR_NENHUM)) {
                throw recusarCampos(linha,
                        "Linha %d: a coluna \"%s\" traz %s junto de nome de campo; %s vale sozinho."
                                .formatted(linha.numero(), COLUNA_CAMPOS_OBRIGATORIOS, VALOR_NENHUM, VALOR_NENHUM));
            }
            if (parte.isBlank() || !parte.equals(parte.strip())) {
                throw recusarCampos(linha,
                        ("Linha %d: a coluna \"%s\" tem nome vazio ou com espaço em volta; use os nomes "
                                + "separados por |, ou %s para declarar que o código não exige campo.")
                                .formatted(linha.numero(), COLUNA_CAMPOS_OBRIGATORIOS, VALOR_NENHUM));
            }
            if (campos.contains(parte)) {
                throw recusarCampos(linha,
                        "Linha %d: a coluna \"%s\" repete o nome \"%s\"."
                                .formatted(linha.numero(), COLUNA_CAMPOS_OBRIGATORIOS, parte));
            }
            campos.add(parte);
        }
        return Optional.of(List.copyOf(campos));
    }

    // Método auxiliar que lê sobre o que incide a redução (D017, 03/10/2026): coluna ausente ou célula em branco é "não declarado", que a R05 lê como alíquota (decisão D1); só aceita ALIQUOTA ou BASE, com maiúsculas, como tributacaoIntegral e natureza.
    private static Optional<IncidenciaDaReducao> reducaoIncideSobre(LinhaCsv linha) {
        if (!linha.valores().containsKey(COLUNA_REDUCAO_INCIDE_SOBRE)) {
            return Optional.empty();
        }
        Optional<String> valor = linha.texto(COLUNA_REDUCAO_INCIDE_SOBRE);
        if (valor.isEmpty()) {
            return Optional.empty();
        }
        for (IncidenciaDaReducao incidencia : IncidenciaDaReducao.values()) {
            if (incidencia.name().equals(valor.get())) {
                return Optional.of(incidencia);
            }
        }
        throw new RecusaDeCampo(linha.numero(), COLUNA_REDUCAO_INCIDE_SOBRE,
                linha.valorComoVeio(COLUNA_REDUCAO_INCIDE_SOBRE),
                "Linha %d: a coluna \"%s\" aceita apenas ALIQUOTA ou BASE, ou fica em branco, mas veio \"%s\"."
                        .formatted(linha.numero(), COLUNA_REDUCAO_INCIDE_SOBRE, valor.get()),
                null);
    }

    // Método auxiliar que monta a recusa da coluna camposObrigatoriosCondicionados, com a linha e o valor como veio.
    private static RecusaDeCampo recusarCampos(LinhaCsv linha, String mensagem) {
        return new RecusaDeCampo(linha.numero(), COLUNA_CAMPOS_OBRIGATORIOS,
                linha.valorComoVeio(COLUNA_CAMPOS_OBRIGATORIOS), mensagem, null);
    }

    // Método auxiliar que monta a recusa da coluna anexosAdmitidos, com a linha e o valor como veio.
    private static RecusaDeCampo recusarAnexos(LinhaCsv linha, String mensagem) {
        return new RecusaDeCampo(linha.numero(), COLUNA_ANEXOS_ADMITIDOS,
                linha.valorComoVeio(COLUNA_ANEXOS_ADMITIDOS), mensagem, null);
    }

    // Método auxiliar que monta a recusa da coluna tributacaoIntegral, com a linha e o valor como veio.
    private static RecusaDeCampo recusarIntegral(LinhaCsv linha, String mensagem) {
        return new RecusaDeCampo(linha.numero(), COLUNA_TRIBUTACAO_INTEGRAL,
                linha.valorComoVeio(COLUNA_TRIBUTACAO_INTEGRAL), mensagem, null);
    }

    // Método auxiliar que diz se o campo veio por tributo; recusa as duas formas juntas e o par pela metade.
    private static boolean estaPorTributo(LinhaCsv linha, Campo campo) {
        Set<String> colunas = linha.valores().keySet();
        boolean temUnica = colunas.contains(campo.colunaUnica());
        boolean temCbs = colunas.contains(campo.colunaCbs());
        boolean temIbs = colunas.contains(campo.colunaIbs());

        // Etapa 12: problema de cabeçalho é do arquivo, e sai uma vez só na recusa, não uma por linha.
        if (temUnica && (temCbs || temIbs)) {
            throw new RecusaDeCabecalho(
                    ("Linha %d: o cabeçalho traz \"%s\" e também a forma por tributo (\"%s\", \"%s\"). "
                            + "Use uma forma só para o campo: com as duas, não há como saber qual vale.")
                            .formatted(linha.numero(), campo.colunaUnica(), campo.colunaCbs(), campo.colunaIbs()));
        }
        if (temCbs != temIbs) {
            throw new RecusaDeCabecalho(
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
