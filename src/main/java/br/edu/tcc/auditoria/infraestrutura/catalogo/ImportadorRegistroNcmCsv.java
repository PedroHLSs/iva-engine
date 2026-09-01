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

/**
 * Importa registros de NCM de um CSV.
 *
 * <p>Colunas esperadas: {@code ncm}, {@code descricao}, mais
 * {@code vigenciaInicio}, {@code vigenciaFim} e {@code fonteNormativa}.</p>
 */
public final class ImportadorRegistroNcmCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_DESCRICAO = "descricao";

    public List<RegistroNcm> importar(Reader origem) throws IOException {
        return LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new).stream()
                .map(this::converter)
                .toList();
    }

    public List<RegistroNcm> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    private RegistroNcm converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String ncm = linha.textoObrigatorio(COLUNA_NCM);
        String descricao = linha.textoObrigatorio(COLUNA_DESCRICAO);

        return linha.converterCom(() -> new RegistroNcm(new Ncm(ncm), descricao, procedencia));
    }
}
