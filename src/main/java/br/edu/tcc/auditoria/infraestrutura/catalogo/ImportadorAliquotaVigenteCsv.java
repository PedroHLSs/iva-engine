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

/**
 * Importa alíquotas de um CSV.
 *
 * <p>Colunas esperadas: {@code tributo}, {@code percentual},
 * {@code abrangencia}, mais {@code vigenciaInicio}, {@code vigenciaFim} e
 * {@code fonteNormativa}.</p>
 *
 * <p>{@code tributo} aceita apenas os nomes de {@link Tributo}. O percentual
 * aceita vírgula ou ponto como separador decimal e mantém a escala declarada no
 * arquivo.</p>
 */
public final class ImportadorAliquotaVigenteCsv {

    public static final String COLUNA_TRIBUTO = "tributo";
    public static final String COLUNA_PERCENTUAL = "percentual";
    public static final String COLUNA_ABRANGENCIA = "abrangencia";

    public List<AliquotaVigente> importar(Reader origem) throws IOException {
        return LeitorCsv.ler(origem, ImportacaoDeCatalogoInvalida::new).stream()
                .map(this::converter)
                .toList();
    }

    public List<AliquotaVigente> importar(Path arquivo) throws IOException {
        try (Reader origem = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            return importar(origem);
        }
    }

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
