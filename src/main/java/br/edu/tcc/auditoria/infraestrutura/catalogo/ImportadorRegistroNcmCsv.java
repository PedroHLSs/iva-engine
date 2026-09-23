package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Classe que importa os registros de NCM de um CSV, com as colunas ncm e descricao, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza.
public final class ImportadorRegistroNcmCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_DESCRICAO = "descricao";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista.
    public TabelaImportada<RegistroNcm> importar(Reader origem) throws IOException {
        List<LinhaCsv> linhas = LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new);
        return new TabelaImportada<>(
                linhas.stream().map(this::converter).toList(),
                NaturezaEmCsv.uniforme(linhas));
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<RegistroNcm> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV num registro de NCM.
    private RegistroNcm converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String ncm = linha.textoObrigatorio(COLUNA_NCM);
        String descricao = linha.textoObrigatorio(COLUNA_DESCRICAO);

        return linha.converterCom(() -> new RegistroNcm(new Ncm(ncm), descricao, procedencia));
    }
}
