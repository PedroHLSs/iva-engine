package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

// Classe que importa os vínculos entre NCM e anexo de um CSV, com as colunas ncm, identificadorDoAnexo e tipoDeTratamento, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza.
public final class ImportadorItemAnexoCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_IDENTIFICADOR_DO_ANEXO = "identificadorDoAnexo";
    public static final String COLUNA_TIPO_DE_TRATAMENTO = "tipoDeTratamento";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista. Mudou na Etapa 12: antes parava na primeira linha recusada; agora lê o arquivo inteiro e recusa com todas as linhas problemáticas numa mensagem só.
    public TabelaImportada<ItemAnexo> importar(Reader origem) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();
        TabelaImportada<ItemAnexo> tabela =
                importar(origem, LeitorDeCatalogoEmCsv.ARQUIVO_ITEM_ANEXO, recusas);
        recusas.lancarSeHouver();
        return tabela;
    }

    // Lê o CSV registrando cada linha recusada em vez de parar na primeira, para a carga ser recusada com todas de uma vez. Acrescentado na Etapa 12.
    TabelaImportada<ItemAnexo> importar(Reader origem, String arquivo, RecusasDaCarga recusas)
            throws IOException {
        return LeituraDeTabela.ler(origem, arquivo, this::converter, recusas);
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<ItemAnexo> importar(Path arquivo) throws IOException {
        // Emenda de 04/10/2026 (D024): abre pelo ponto único, em UTF-8 estrito, como a pasta e o envio.
        try (Reader origem = AberturaEmUtf8.abrir(arquivo, RecusaDoCatalogoEmCsv.INSTANCIA)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV num vínculo entre NCM e anexo.
    private ItemAnexo converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String ncm = linha.textoObrigatorio(COLUNA_NCM);
        String identificadorDoAnexo = linha.textoObrigatorio(COLUNA_IDENTIFICADOR_DO_ANEXO);
        String tipoDeTratamento = linha.textoObrigatorio(COLUNA_TIPO_DE_TRATAMENTO);

        // Etapa 12: NCM e anexo são convertidos à parte, para a recusa dizer a coluna e o valor.
        Ncm ncmValido = linha.converterCom(COLUNA_NCM, () -> new Ncm(ncm));
        IdentificadorAnexo anexoValido = linha.converterCom(
                COLUNA_IDENTIFICADOR_DO_ANEXO, () -> new IdentificadorAnexo(identificadorDoAnexo));
        return linha.converterCom(() -> new ItemAnexo(
                ncmValido,
                anexoValido,
                tipoDeTratamento,
                procedencia));
    }
}
