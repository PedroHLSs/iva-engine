package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Importa classificações tributárias de um CSV.
 *
 * <p>Colunas esperadas: {@code codigo}, {@code cstsCompativeis},
 * {@code dispositivoLegal}, {@code indicadorDeBeneficio},
 * {@code percentualReducao}, {@code camposObrigatoriosCondicionados}, mais as
 * três comuns a todo catálogo — {@code vigenciaInicio}, {@code vigenciaFim} e
 * {@code fonteNormativa}.</p>
 *
 * <p>{@code cstsCompativeis} e {@code camposObrigatoriosCondicionados} são
 * listas dentro de um único campo, separadas por {@code |}.</p>
 */
public final class ImportadorClassificacaoTributariaCsv {

    public static final String COLUNA_CODIGO = "codigo";
    public static final String COLUNA_CSTS_COMPATIVEIS = "cstsCompativeis";
    public static final String COLUNA_DISPOSITIVO_LEGAL = "dispositivoLegal";
    public static final String COLUNA_INDICADOR_DE_BENEFICIO = "indicadorDeBeneficio";
    public static final String COLUNA_PERCENTUAL_REDUCAO = "percentualReducao";
    public static final String COLUNA_CAMPOS_OBRIGATORIOS = "camposObrigatoriosCondicionados";

    public List<ClassificacaoTributaria> importar(Reader origem) throws IOException {
        return LeitorCsv.ler(origem).stream().map(this::converter).toList();
    }

    public List<ClassificacaoTributaria> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

    private ClassificacaoTributaria converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String codigo = linha.textoObrigatorio(COLUNA_CODIGO);
        List<String> csts = linha.lista(COLUNA_CSTS_COMPATIVEIS);
        String dispositivoLegal = linha.textoObrigatorio(COLUNA_DISPOSITIVO_LEGAL);
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
                    linha.decimal(COLUNA_PERCENTUAL_REDUCAO),
                    camposObrigatorios,
                    procedencia);
        });
    }
}
