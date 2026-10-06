package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

// Classe que importa os registros de NCM de um CSV, com as colunas ncm e descricao, mais vigenciaInicio, vigenciaFim, fonteNormativa e natureza.
public final class ImportadorRegistroNcmCsv {

    public static final String COLUNA_NCM = "ncm";
    public static final String COLUNA_DESCRICAO = "descricao";

    // Lê o CSV e devolve os registros junto com a procedência. Mudou na Etapa 11: antes devolvia só a lista. Mudou na Etapa 12: antes parava na primeira linha recusada; agora lê o arquivo inteiro e recusa com todas as linhas problemáticas numa mensagem só.
    public TabelaImportada<RegistroNcm> importar(Reader origem) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();
        TabelaImportada<RegistroNcm> tabela =
                importar(origem, LeitorDeCatalogoEmCsv.ARQUIVO_REGISTRO_NCM, recusas);
        recusas.lancarSeHouver();
        return tabela;
    }

    // Lê o CSV registrando cada linha recusada em vez de parar na primeira, para a carga ser recusada com todas de uma vez. Acrescentado na Etapa 12.
    TabelaImportada<RegistroNcm> importar(Reader origem, String arquivo, RecusasDaCarga recusas)
            throws IOException {
        return LeituraDeTabela.ler(origem, arquivo, this::converter, recusas);
    }

    // Abre o arquivo em UTF-8 e importa.
    public TabelaImportada<RegistroNcm> importar(Path arquivo) throws IOException {
        // Emenda de 04/10/2026 (D024): abre pelo ponto único, em UTF-8 estrito, como a pasta e o envio.
        try (Reader origem = AberturaEmUtf8.abrir(arquivo, RecusaDoCatalogoEmCsv.INSTANCIA)) {
            return importar(origem);
        }
    }

    // Método auxiliar que transforma uma linha do CSV num registro de NCM.
    private RegistroNcm converter(LinhaCsv linha) {
        ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);

        String ncm = linha.textoObrigatorio(COLUNA_NCM);
        String descricao = linha.textoObrigatorio(COLUNA_DESCRICAO);

        // Etapa 12: o NCM é convertido à parte, para a recusa dizer a coluna e o valor.
        Ncm ncmValido = linha.converterCom(COLUNA_NCM, () -> new Ncm(ncm));
        return linha.converterCom(() -> new RegistroNcm(ncmValido, descricao, procedencia));
    }
}
