package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

// Classe que importa as alíquotas de um CSV, com as colunas tributo, percentual e abrangencia, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza. O percentual aceita vírgula ou ponto e mantém as casas decimais do arquivo.
public final class ImportadorAliquotaVigenteCsv {

    public static final String COLUNA_TRIBUTO = "tributo";
    public static final String COLUNA_PERCENTUAL = "percentual";
    public static final String COLUNA_ABRANGENCIA = "abrangencia";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista.
    public TabelaImportada<AliquotaVigente> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<AliquotaVigente> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV numa alíquota; percentual em branco é recusado.
    private AliquotaVigente converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        Tributo tributo = converterTributo(linha);
        BigDecimal percentual = linha.decimal(COLUNA_PERCENTUAL)
                .orElseThrow(() -> new ImportacaoDeCatalogoInvalida(
                        "Linha %d: a coluna \"%s\" é obrigatória e veio em branco."
                                .formatted(linha.numero(), COLUNA_PERCENTUAL)));
        String abrangencia = linha.textoObrigatorio(COLUNA_ABRANGENCIA);

        return linha.converterCom(() ->
                new AliquotaVigente(tributo, percentual, new Abrangencia(abrangencia), procedencia));
    }

    // Método auxiliar que converte o texto no tributo; só aceita os nomes do enum Tributo.
    private static Tributo converterTributo(LinhaCsv linha) {
        String informado = linha.textoObrigatorio(COLUNA_TRIBUTO);
        return Arrays.stream(Tributo.values())
                .filter(tributo -> tributo.name().equals(informado))
                .findFirst()
                .orElseThrow(() -> new ImportacaoDeCatalogoInvalida(
                        "Linha %d: tributo desconhecido \"%s\". Valores aceitos: %s."
                                .formatted(linha.numero(), informado, Arrays.toString(Tributo.values()))));
    }
}
