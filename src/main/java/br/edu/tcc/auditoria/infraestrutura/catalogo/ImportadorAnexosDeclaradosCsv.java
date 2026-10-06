package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// Classe que importa a lista de anexos declarados de um CSV, com as colunas identificadorDoAnexo, tipoDeCodigo (NCM, NBS ou NCM_E_NBS), vigenciaInicio e vigenciaFim opcionais, fonteNormativa e natureza. Sem vigência, o anexo existe mas não está carregado; fim sem início é recusado; anexo repetido é recusado. Acrescentada em 01/10/2026 (decisões D6, D7 e D9 do usuário).
public final class ImportadorAnexosDeclaradosCsv {

    public static final String COLUNA_IDENTIFICADOR_DO_ANEXO = "identificadorDoAnexo";
    public static final String COLUNA_TIPO_DE_CODIGO = "tipoDeCodigo";

    // Lê o CSV e devolve os anexos junto com a procedência, recusando o arquivo inteiro com todas as linhas problemáticas.
    public TabelaImportada<AnexoDeclarado> importar(Reader origem) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();
        TabelaImportada<AnexoDeclarado> tabela =
                importar(origem, LeitorDeCatalogoEmCsv.ARQUIVO_ANEXOS_DECLARADOS, recusas);
        recusas.lancarSeHouver();
        return tabela;
    }

    // Lê o CSV registrando cada linha recusada em vez de parar na primeira.
    TabelaImportada<AnexoDeclarado> importar(Reader origem, String arquivo, RecusasDaCarga recusas)
            throws IOException {
        Set<String> vistos = new HashSet<>();
        return LeituraDeTabela.ler(origem, arquivo, linha -> converter(linha, vistos), recusas);
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<AnexoDeclarado> importar(Path arquivo) throws IOException {
        // Emenda de 04/10/2026 (D024): abre pelo ponto único, em UTF-8 estrito, como a pasta e o envio.
        try (Reader origem = AberturaEmUtf8.abrir(arquivo, RecusaDoCatalogoEmCsv.INSTANCIA)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV num anexo declarado.
    private static AnexoDeclarado converter(LinhaCsv linha, Set<String> vistos) {
        String identificador = linha.textoObrigatorio(COLUNA_IDENTIFICADOR_DO_ANEXO);
        IdentificadorAnexo identificadorValido = linha.converterCom(
                COLUNA_IDENTIFICADOR_DO_ANEXO, () -> new IdentificadorAnexo(identificador));
        if (!vistos.add(identificador)) {
            throw new RecusaDeCampo(linha.numero(), COLUNA_IDENTIFICADOR_DO_ANEXO,
                    linha.valorComoVeio(COLUNA_IDENTIFICADOR_DO_ANEXO),
                    "Linha %d: o anexo \"%s\" já foi declarado numa linha anterior; declare cada anexo uma vez só."
                            .formatted(linha.numero(), identificador),
                    null);
        }

        String tipo = linha.textoObrigatorio(COLUNA_TIPO_DE_CODIGO);
        TipoDeCodigoDoAnexo tipoValido = Arrays.stream(TipoDeCodigoDoAnexo.values())
                .filter(valor -> valor.name().equals(tipo))
                .findFirst()
                .orElseThrow(() -> new RecusaDeCampo(linha.numero(), COLUNA_TIPO_DE_CODIGO,
                        linha.valorComoVeio(COLUNA_TIPO_DE_CODIGO),
                        "Linha %d: a coluna \"%s\" aceita apenas %s, mas veio \"%s\"."
                                .formatted(linha.numero(), COLUNA_TIPO_DE_CODIGO,
                                        Arrays.toString(TipoDeCodigoDoAnexo.values()), tipo),
                        null));

        Optional<LocalDate> inicio = linha.data(ProcedenciaEmCsv.COLUNA_VIGENCIA_INICIO);
        Optional<LocalDate> fim = linha.data(ProcedenciaEmCsv.COLUNA_VIGENCIA_FIM);
        if (inicio.isEmpty() && fim.isPresent()) {
            throw new RecusaDeCampo(linha.numero(), ProcedenciaEmCsv.COLUNA_VIGENCIA_INICIO,
                    linha.valorComoVeio(ProcedenciaEmCsv.COLUNA_VIGENCIA_INICIO),
                    ("Linha %d: o anexo \"%s\" tem fim de vigência sem início. Sem início, o anexo existe mas "
                            + "não está carregado; com início, informe os dois.")
                            .formatted(linha.numero(), identificador),
                    null);
        }
        Optional<PeriodoVigencia> carregamento = linha.converterCom(() -> inicio.map(
                comeco -> fim.map(termino -> PeriodoVigencia.de(comeco, termino))
                        .orElseGet(() -> PeriodoVigencia.aPartirDe(comeco))));
        // D022 (04/10/2026): fonte sem nenhuma letra, como "0", é recusada.
        String fonte = TextoQueIdentificaNorma.exigir(linha, ProcedenciaEmCsv.COLUNA_FONTE_NORMATIVA,
                linha.textoObrigatorio(ProcedenciaEmCsv.COLUNA_FONTE_NORMATIVA));

        return linha.converterCom(() -> new AnexoDeclarado(identificadorValido, tipoValido, carregamento, fonte));
    }
}
