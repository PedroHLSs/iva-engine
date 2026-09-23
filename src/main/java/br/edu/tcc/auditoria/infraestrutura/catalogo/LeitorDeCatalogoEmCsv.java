package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

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

// Classe que monta uma carga de catálogo a partir de uma pasta com cinco CSV obrigatórios: os quatro de dados, cada um com a coluna natureza, e o cobertura.csv, que diz o período e a fonte que a carga cobre em cada tabela. Arquivo que falta é recusado, e não vira tabela vazia.
public final class LeitorDeCatalogoEmCsv {

    static final String ARQUIVO_CLASSIFICACAO_TRIBUTARIA = "classificacao-tributaria.csv";
    static final String ARQUIVO_REGISTRO_NCM = "registro-ncm.csv";
    static final String ARQUIVO_ITEM_ANEXO = "item-anexo.csv";
    static final String ARQUIVO_ALIQUOTA_VIGENTE = "aliquota-vigente.csv";
    static final String ARQUIVO_COBERTURA = "cobertura.csv";

    static final String COLUNA_TABELA = "tabela";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private LeitorDeCatalogoEmCsv() {
    }

    // Método estático que devolve os nomes dos arquivos esperados, para a mensagem de ajuda do comando.
    public static String arquivosEsperados() {
        return String.join(", ",
                ARQUIVO_CLASSIFICACAO_TRIBUTARIA,
                ARQUIVO_REGISTRO_NCM,
                ARQUIVO_ITEM_ANEXO,
                ARQUIVO_ALIQUOTA_VIGENTE,
                ARQUIVO_COBERTURA);
    }

    // Método estático que lê os cinco arquivos da pasta e monta a carga com a versão informada.
    public static CargaDeCatalogo ler(Path diretorio, String versao) throws IOException {
        if (diretorio == null) {
            throw new ImportacaoDeCatalogoInvalida("Não foi informado o diretório do catálogo.");
        }
        if (!Files.isDirectory(diretorio)) {
            throw new ImportacaoDeCatalogoInvalida(
                    "\"%s\" não é um diretório.".formatted(diretorio));
        }

        TabelaImportada<ClassificacaoTributaria> classificacoes =
                new ImportadorClassificacaoTributariaCsv()
                        .importar(exigir(diretorio, ARQUIVO_CLASSIFICACAO_TRIBUTARIA));
        TabelaImportada<RegistroNcm> ncms = new ImportadorRegistroNcmCsv()
                .importar(exigir(diretorio, ARQUIVO_REGISTRO_NCM));
        TabelaImportada<ItemAnexo> anexos = new ImportadorItemAnexoCsv()
                .importar(exigir(diretorio, ARQUIVO_ITEM_ANEXO));
        TabelaImportada<AliquotaVigente> aliquotas = new ImportadorAliquotaVigenteCsv()
                .importar(exigir(diretorio, ARQUIVO_ALIQUOTA_VIGENTE));

        return new CargaDeCatalogo(
                versao,
                lerCobertura(exigir(diretorio, ARQUIVO_COBERTURA)),
                new NaturezaDaCarga(
                        classificacoes.natureza(),
                        ncms.natureza(),
                        anexos.natureza(),
                        aliquotas.natureza()),
                classificacoes.registros(),
                ncms.registros(),
                anexos.registros(),
                aliquotas.registros());
    }

    // Método auxiliar que lê o cobertura.csv; recusa tabela desconhecida, tabela repetida e tabela sem cobertura declarada.
    private static CoberturaDoCatalogo lerCobertura(Path arquivo) throws IOException {
        Map<String, ProcedenciaNormativa> porTabela = new LinkedHashMap<>();
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            for (LinhaCsv linha : LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new)) {
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

    // Método auxiliar que confere se o nome é de uma das tabelas conhecidas.
    private static boolean ehTabelaConhecida(String tabela) {
        return Arrays.stream(TabelaNormativa.values())
                .anyMatch(conhecida -> conhecida.name().equals(tabela));
    }

    // Método auxiliar que devolve o caminho do arquivo; recusa se ele não existir, porque arquivo que falta não é tabela vazia.
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
