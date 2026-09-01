package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta uma carga de catálogo a partir de um diretório de arquivos CSV.
 *
 * <h2>Cinco arquivos, todos obrigatórios</h2>
 *
 * <p>Os quatro de dados e o de cobertura. Arquivo ausente não é interpretado
 * como tabela vazia: seria impossível distinguir "esta carga não traz alíquota
 * nenhuma" de "esqueci de gerar o arquivo de alíquotas", e a diferença muda o
 * relatório inteiro. Para declarar tabela sem registros, forneça o arquivo só
 * com o cabeçalho — aí a ausência foi dita, e não suposta.</p>
 *
 * <h2>O arquivo de cobertura</h2>
 *
 * <p>{@code cobertura.csv} declara, por tabela, o período e a fonte normativa
 * que a carga cobre. Tem uma linha para cada uma das tabelas de
 * {@link TabelaNormativa}, com as colunas {@code tabela}, {@code vigenciaInicio},
 * {@code vigenciaFim} e {@code fonteNormativa} — as três últimas iguais às dos
 * demais arquivos.</p>
 *
 * <p>Sem essa declaração, o sistema não teria como separar "o catálogo foi
 * carregado para esta data e não traz este registro", que é apontamento, de
 * "esta tabela não foi carregada para esta data", que é não avaliado. Ele não
 * deduz a cobertura a partir das linhas importadas: uma carga incompleta
 * pareceria completa e produziria apontamento inventado.</p>
 */
public final class LeitorDeCatalogoEmCsv {

    static final String ARQUIVO_CLASSIFICACAO_TRIBUTARIA = "classificacao-tributaria.csv";
    static final String ARQUIVO_REGISTRO_NCM = "registro-ncm.csv";
    static final String ARQUIVO_ITEM_ANEXO = "item-anexo.csv";
    static final String ARQUIVO_ALIQUOTA_VIGENTE = "aliquota-vigente.csv";
    static final String ARQUIVO_COBERTURA = "cobertura.csv";

    static final String COLUNA_TABELA = "tabela";

    private LeitorDeCatalogoEmCsv() {
    }

    /** Os nomes de arquivo esperados, para a mensagem de modo de usar. */
    public static String arquivosEsperados() {
        return String.join(", ",
                ARQUIVO_CLASSIFICACAO_TRIBUTARIA,
                ARQUIVO_REGISTRO_NCM,
                ARQUIVO_ITEM_ANEXO,
                ARQUIVO_ALIQUOTA_VIGENTE,
                ARQUIVO_COBERTURA);
    }

    /** Lê os cinco arquivos do diretório e monta a carga com a versão indicada. */
    public static CargaDeCatalogo ler(Path diretorio, String versao) throws IOException {
        if (diretorio == null) {
            throw new ImportacaoDeCatalogoInvalida("Não foi informado o diretório do catálogo.");
        }
        if (!Files.isDirectory(diretorio)) {
            throw new ImportacaoDeCatalogoInvalida(
                    "\"%s\" não é um diretório.".formatted(diretorio));
        }

        return new CargaDeCatalogo(
                versao,
                lerCobertura(exigir(diretorio, ARQUIVO_COBERTURA)),
                new ImportadorClassificacaoTributariaCsv()
                        .importar(exigir(diretorio, ARQUIVO_CLASSIFICACAO_TRIBUTARIA)),
                new ImportadorRegistroNcmCsv()
                        .importar(exigir(diretorio, ARQUIVO_REGISTRO_NCM)),
                new ImportadorItemAnexoCsv()
                        .importar(exigir(diretorio, ARQUIVO_ITEM_ANEXO)),
                new ImportadorAliquotaVigenteCsv()
                        .importar(exigir(diretorio, ARQUIVO_ALIQUOTA_VIGENTE)));
    }

    private static CoberturaDoCatalogo lerCobertura(Path arquivo) throws IOException {
        Map<String, ProcedenciaNormativa> porTabela = new LinkedHashMap<>();
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            for (LinhaCsv linha : LeitorCsv.ler(origem)) {
                String tabela = linha.textoObrigatorio(COLUNA_TABELA);
                if (!ehTabelaConhecida(tabela)) {
                    throw new ImportacaoDeCatalogoInvalida(
                            "Linha %d de %s: tabela desconhecida \"%s\". Valores aceitos: %s."
                                    .formatted(linha.numero(), arquivo.getFileName(), tabela,
                                            Arrays.toString(TabelaNormativa.values())));
                }
                if (porTabela.put(tabela, ProcedenciaEmCsv.ler(linha)) != null) {
                    throw new ImportacaoDeCatalogoInvalida(
                            "Linha %d de %s: a tabela \"%s\" teve cobertura declarada duas vezes."
                                    .formatted(linha.numero(), arquivo.getFileName(), tabela));
                }
            }
        }

        List<String> faltando = new ArrayList<>();
        for (TabelaNormativa tabela : TabelaNormativa.values()) {
            if (!porTabela.containsKey(tabela.name())) {
                faltando.add(tabela.name());
            }
        }
        if (!faltando.isEmpty()) {
            throw new ImportacaoDeCatalogoInvalida(
                    ("%s não declara cobertura para: %s. Sem essa declaração o sistema não distingue "
                            + "registro ausente do catálogo de tabela não carregada, e apontaria com "
                            + "base em silêncio.")
                            .formatted(arquivo.getFileName(), String.join(", ", faltando)));
        }

        return new CoberturaDoCatalogo(
                porTabela.get(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA.name()),
                porTabela.get(TabelaNormativa.NCM.name()),
                porTabela.get(TabelaNormativa.ITEM_ANEXO.name()));
    }

    private static boolean ehTabelaConhecida(String tabela) {
        return Arrays.stream(TabelaNormativa.values())
                .anyMatch(conhecida -> conhecida.name().equals(tabela));
    }

    private static Path exigir(Path diretorio, String nomeDoArquivo) {
        Path arquivo = diretorio.resolve(nomeDoArquivo);
        if (!Files.isRegularFile(arquivo)) {
            throw new ImportacaoDeCatalogoInvalida(
                    ("Falta o arquivo \"%s\" em \"%s\". Arquivo ausente não é lido como tabela vazia: "
                            + "para declarar uma tabela sem registros, forneça o arquivo apenas com o "
                            + "cabeçalho.").formatted(nomeDoArquivo, diretorio));
        }
        return arquivo;
    }
}
