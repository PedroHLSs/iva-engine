package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Importa vínculos entre NCM e anexo de um CSV.
 *
 * <p>Colunas esperadas: {@code ncm}, {@code identificadorDoAnexo},
 * {@code tipoDeTratamento}, mais {@code vigenciaInicio}, {@code vigenciaFim} e
 * {@code fonteNormativa}.</p>
 */
public final class ImportadorItemAnexoCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_IDENTIFICADOR_DO_ANEXO = "identificadorDoAnexo";
    public static final String COLUNA_TIPO_DE_TRATAMENTO = "tipoDeTratamento";

    /*
     * Emenda da etapa de conferência, sobre a Etapa 2.
     *
     * O retorno passou de List para TabelaImportada porque a procedência das
     * linhas é fato sobre elas, e precisa sair pelo mesmo caminho. Devolvê-la
     * à parte permitiria ler os registros de um arquivo e a natureza de outro.
     */
    public TabelaImportada<ItemAnexo> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    public TabelaImportada<ItemAnexo> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    private ItemAnexo converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String ncm = linha.textoObrigatorio(COLUNA_NCM);
        String identificadorDoAnexo = linha.textoObrigatorio(COLUNA_IDENTIFICADOR_DO_ANEXO);
        String tipoDeTratamento = linha.textoObrigatorio(COLUNA_TIPO_DE_TRATAMENTO);

        return linha.converterCom(() -> new ItemAnexo(
                new Ncm(ncm),
                new IdentificadorAnexo(identificadorDoAnexo),
                tipoDeTratamento,
                procedencia));
    }
}
