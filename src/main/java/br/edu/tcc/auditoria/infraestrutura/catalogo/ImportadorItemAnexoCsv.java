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

// Classe que importa os vínculos entre NCM e anexo de um CSV, com as colunas ncm, identificadorDoAnexo e tipoDeTratamento, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza.
public final class ImportadorItemAnexoCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_IDENTIFICADOR_DO_ANEXO = "identificadorDoAnexo";
    public static final String COLUNA_TIPO_DE_TRATAMENTO = "tipoDeTratamento";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista.
    public TabelaImportada<ItemAnexo> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<ItemAnexo> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV num vínculo entre NCM e anexo.
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
