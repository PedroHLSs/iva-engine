package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

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

    public List<ItemAnexo> importar(Reader origem) throws IOException {
        return LeitorCsv.ler(origem).stream().map(this::converter).toList();
    }

    public List<ItemAnexo> importar(Path arquivo) throws IOException {
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
