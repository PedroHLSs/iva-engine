package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.Abrangencia;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.Tributo;
import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;

// Classe que importa as alíquotas de um CSV, com as colunas tributo, percentual e abrangencia, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza. O percentual aceita vírgula ou ponto e mantém as casas decimais do arquivo.
public final class ImportadorAliquotaVigenteCsv {

    public static final String COLUNA_TRIBUTO = "tributo";
    public static final String COLUNA_PERCENTUAL = "percentual";
    public static final String COLUNA_ABRANGENCIA = "abrangencia";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista. Mudou na Etapa 12: antes parava na primeira linha recusada; agora lê o arquivo inteiro e recusa com todas as linhas problemáticas numa mensagem só.
    public TabelaImportada<AliquotaVigente> importar(Reader origem) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();
        TabelaImportada<AliquotaVigente> tabela =
                importar(origem, LeitorDeCatalogoEmCsv.ARQUIVO_ALIQUOTA_VIGENTE, recusas);
        recusas.lancarSeHouver();
        return tabela;
    }

    // Lê o CSV registrando cada linha recusada em vez de parar na primeira, para a carga ser recusada com todas de uma vez. Acrescentado na Etapa 12.
    TabelaImportada<AliquotaVigente> importar(Reader origem, String arquivo, RecusasDaCarga recusas)
            throws IOException {
        return LeituraDeTabela.ler(origem, arquivo, this::converter, recusas);
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<AliquotaVigente> importar(Path arquivo) throws IOException {
        // Emenda de 04/10/2026 (D024): abre pelo ponto único, em UTF-8 estrito, como a pasta e o envio.
        try (Reader origem = AberturaEmUtf8.abrir(arquivo, RecusaDoCatalogoEmCsv.INSTANCIA)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV numa alíquota; percentual em branco é recusado.
    private AliquotaVigente converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        Tributo tributo = converterTributo(linha);
        BigDecimal percentual = linha.decimal(COLUNA_PERCENTUAL)
                .orElseThrow(() -> new RecusaDeCampo(linha.numero(), COLUNA_PERCENTUAL, "",
                        "Linha %d: a coluna \"%s\" é obrigatória e veio em branco."
                                .formatted(linha.numero(), COLUNA_PERCENTUAL),
                        null));
        String abrangencia = linha.textoObrigatorio(COLUNA_ABRANGENCIA);

        // Etapa 12: a abrangência é convertida à parte, para a recusa dizer a coluna e o valor.
        Abrangencia abrangenciaValida = linha.converterCom(
                COLUNA_ABRANGENCIA, () -> new Abrangencia(abrangencia));
        return linha.converterCom(() ->
                new AliquotaVigente(tributo, percentual, abrangenciaValida, procedencia));
    }

    // Método auxiliar que converte o texto no tributo; só aceita os nomes do enum Tributo.
    private static Tributo converterTributo(LinhaCsv linha) {
        String informado = linha.textoObrigatorio(COLUNA_TRIBUTO);
        return Arrays.stream(Tributo.values())
                .filter(tributo -> tributo.name().equals(informado))
                .findFirst()
                .orElseThrow(() -> new RecusaDeCampo(linha.numero(), COLUNA_TRIBUTO, informado,
                        "Linha %d: tributo desconhecido \"%s\". Valores aceitos: %s."
                                .formatted(linha.numero(), informado, Arrays.toString(Tributo.values())),
                        null));
    }
}
