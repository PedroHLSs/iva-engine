package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.GuardaDeCoberturaSobreTabelaVazia;
import br.edu.tcc.auditoria.aplicacao.catalogo.Natureza;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.SubstituicaoDeTabelas;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaNormativa;
import br.edu.tcc.auditoria.aplicacao.catalogo.TabelaSubstituta;
import br.edu.tcc.auditoria.dominio.excecao.ExcecaoDeDominio;
import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.csv.LeitorCsv;
import br.edu.tcc.auditoria.infraestrutura.csv.LinhaCsv;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Classe que monta uma carga de catálogo a partir de uma pasta com cinco CSV obrigatórios: os quatro de dados, cada um com a coluna natureza, e o cobertura.csv, que diz o período e a fonte que a carga cobre em cada tabela. Arquivo que falta é recusado, e não vira tabela vazia. Emenda da Etapa 12: os arquivos podem vir também de um envio pela web; a carga é recusada inteira com todos os problemas de todos os arquivos numa mensagem só; e cobertura declarada sobre tabela sem registro é recusada. Emenda de 30/09/2026 (R03 1.1.0): anexo citado em anexosAdmitidos que não existe em item-anexo.csv recusa a carga inteira. Emenda de 01/10/2026 (D9): essa checagem foi substituída pela checagem contra o anexos-declarados.csv, arquivo opcional que lista os anexos válidos e diz quais estão carregados; sem ele, carga com código que cita anexo é recusada.
public final class LeitorDeCatalogoEmCsv {

    static final String ARQUIVO_CLASSIFICACAO_TRIBUTARIA = "classificacao-tributaria.csv";
    static final String ARQUIVO_REGISTRO_NCM = "registro-ncm.csv";
    static final String ARQUIVO_ITEM_ANEXO = "item-anexo.csv";
    static final String ARQUIVO_ALIQUOTA_VIGENTE = "aliquota-vigente.csv";
    static final String ARQUIVO_COBERTURA = "cobertura.csv";
    static final String ARQUIVO_ANEXOS_DECLARADOS = "anexos-declarados.csv";

    static final String COLUNA_TABELA = "tabela";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private LeitorDeCatalogoEmCsv() {
    }

    // Método estático que devolve os nomes dos arquivos esperados, para a mensagem de ajuda do comando.
    public static String arquivosEsperados() {
        return String.join(", ",
                ARQUIVO_CLASSIFICACAO_TRIBUTARIA,
                ARQUIVO_REGISTRO_NCM,
                ARQUIVO_ITEM_ANEXO,
                ARQUIVO_ALIQUOTA_VIGENTE,
                ARQUIVO_COBERTURA);
    }

    // Método estático que devolve os nomes de todos os arquivos que a carga aceita: os cinco obrigatórios e o anexos-declarados.csv, que é opcional. Acrescentado em 04/10/2026 (D026, decisão D-c): o envio pela web comparava o nome com arquivosEsperados(), que não lista o opcional, e recusava o anexos-declarados.csv — carga com código que admite anexo só importava pela linha de comando.
    public static String arquivosAceitos() {
        return arquivosEsperados() + ", " + ARQUIVO_ANEXOS_DECLARADOS;
    }

    // Método estático que diz se vieram os cinco arquivos obrigatórios. Com os cinco, a importação é completa e não herda nada; sem algum deles, é parcial, e o que faltar vem de uma carga existente. Acrescentado em 04/10/2026 (D026).
    public static boolean estaCompleto(FontesDoCatalogo fontes) throws IOException {
        for (String arquivo : List.of(ARQUIVO_CLASSIFICACAO_TRIBUTARIA, ARQUIVO_REGISTRO_NCM, ARQUIVO_ITEM_ANEXO,
                ARQUIVO_ALIQUOTA_VIGENTE, ARQUIVO_COBERTURA)) {
            if (!veio(fontes, arquivo)) {
                return false;
            }
        }
        return true;
    }

    // Método estático que lê os cinco arquivos da pasta e monta a carga com a versão informada. Mudou na Etapa 12: antes parava no primeiro problema; agora lê os cinco arquivos até o fim, recusa a carga inteira com todos os problemas numa mensagem só, e recusa cobertura declarada sobre tabela sem registro.
    public static CargaDeCatalogo ler(Path diretorio, String versao) throws IOException {
        if (diretorio == null) {
            throw new ImportacaoDeCatalogoInvalida("Não foi informado o diretório do catálogo.");
        }
        if (!Files.isDirectory(diretorio)) {
            throw new ImportacaoDeCatalogoInvalida(
                    "\"%s\" não é um diretório.".formatted(diretorio));
        }
        return ler(FontesDoCatalogo.daPasta(diretorio), versao);
    }

    // Método estático que lê os cinco arquivos de qualquer origem, pasta ou envio pela web, e monta a carga. Acrescentado na Etapa 12, para os dois caminhos terem as mesmas recusas.
    public static CargaDeCatalogo ler(FontesDoCatalogo fontes, String versao) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();

        TabelaImportada<ClassificacaoTributaria> classificacoes = lerTabela(fontes,
                ARQUIVO_CLASSIFICACAO_TRIBUTARIA, recusas, true, (origem, arquivo) ->
                        new ImportadorClassificacaoTributariaCsv().importar(origem, arquivo, recusas));
        TabelaImportada<RegistroNcm> ncms = lerTabela(fontes, ARQUIVO_REGISTRO_NCM, recusas, true,
                (origem, arquivo) -> new ImportadorRegistroNcmCsv().importar(origem, arquivo, recusas));
        TabelaImportada<ItemAnexo> anexos = lerTabela(fontes, ARQUIVO_ITEM_ANEXO, recusas, true,
                (origem, arquivo) -> new ImportadorItemAnexoCsv().importar(origem, arquivo, recusas));
        TabelaImportada<AliquotaVigente> aliquotas = lerTabela(fontes, ARQUIVO_ALIQUOTA_VIGENTE, recusas,
                true, (origem, arquivo) ->
                        new ImportadorAliquotaVigenteCsv().importar(origem, arquivo, recusas));
        Optional<CoberturaLida> cobertura = lerCobertura(fontes, recusas, true);
        boolean vieramOsAnexosDeclarados = veio(fontes, ARQUIVO_ANEXOS_DECLARADOS);
        TabelaImportada<AnexoDeclarado> declarados = lerTabela(fontes, ARQUIVO_ANEXOS_DECLARADOS, recusas, false,
                (origem, arquivo) -> new ImportadorAnexosDeclaradosCsv().importar(origem, arquivo, recusas));
        exigirAnexosAdmitidosDeclarados(classificacoes.registros(), declarados.registros(),
                vieramOsAnexosDeclarados, recusas);

        recusas.lancarSeHouver();

        CoberturaDoCatalogo lida = cobertura.orElseThrow().cobertura();
        CargaDeCatalogo carga = new CargaDeCatalogo(
                versao,
                new CoberturaDoCatalogo(lida.classificacoesTributarias(), lida.ncm(), lida.itensDeAnexo(),
                        declarados.registros()),
                new NaturezaDaCarga(
                        classificacoes.natureza(),
                        ncms.natureza(),
                        anexos.natureza(),
                        aliquotas.natureza(),
                        declarados.natureza(),
                        Optional.of(cobertura.orElseThrow().natureza())),
                classificacoes.registros(),
                ncms.registros(),
                anexos.registros(),
                aliquotas.registros());
        GuardaDeCoberturaSobreTabelaVazia.exigir(carga);
        return carga;
    }

    // Método estático que lê só os arquivos que vieram, para editar uma carga: cada um substitui a tabela correspondente, e o que não veio é copiado da carga de origem. Acrescentado na Etapa 12.
    public static SubstituicaoDeTabelas lerSubstituicao(FontesDoCatalogo fontes) throws IOException {
        RecusasDaCarga recusas = new RecusasDaCarga();

        Optional<TabelaSubstituta<ClassificacaoTributaria>> classificacoes = substituta(fontes,
                ARQUIVO_CLASSIFICACAO_TRIBUTARIA, recusas, (origem, arquivo) ->
                        new ImportadorClassificacaoTributariaCsv().importar(origem, arquivo, recusas));
        Optional<TabelaSubstituta<RegistroNcm>> ncms = substituta(fontes, ARQUIVO_REGISTRO_NCM, recusas,
                (origem, arquivo) -> new ImportadorRegistroNcmCsv().importar(origem, arquivo, recusas));
        Optional<TabelaSubstituta<ItemAnexo>> anexos = substituta(fontes, ARQUIVO_ITEM_ANEXO, recusas,
                (origem, arquivo) -> new ImportadorItemAnexoCsv().importar(origem, arquivo, recusas));
        Optional<TabelaSubstituta<AliquotaVigente>> aliquotas = substituta(fontes, ARQUIVO_ALIQUOTA_VIGENTE,
                recusas, (origem, arquivo) ->
                        new ImportadorAliquotaVigenteCsv().importar(origem, arquivo, recusas));
        Optional<CoberturaLida> cobertura = lerCobertura(fontes, recusas, false);
        Optional<TabelaSubstituta<AnexoDeclarado>> declarados = substituta(fontes, ARQUIVO_ANEXOS_DECLARADOS,
                recusas, (origem, arquivo) -> new ImportadorAnexosDeclaradosCsv().importar(origem, arquivo, recusas));
        // Só dá para conferir aqui quando os dois arquivos vieram; nos demais casos a conferência é feita ao montar a carga nova, com a lista herdada da origem.
        // Emenda de 04/10/2026 (D026): "ao montar a carga nova" quer dizer no construtor de CargaDeCatalogo, que recusa anexo admitido fora da lista em qualquer caminho, e não em SubstituicaoDeTabelas.aplicarSobre, que só junta as tabelas. A recusa sai numa mensagem só, com todos os códigos, e não na tabela de linhas.
        if (classificacoes.isPresent() && declarados.isPresent()) {
            exigirAnexosAdmitidosDeclarados(classificacoes.get().registros(), declarados.get().registros(),
                    true, recusas);
        }

        recusas.lancarSeHouver();
        return new SubstituicaoDeTabelas(classificacoes, ncms, anexos, aliquotas,
                cobertura.map(CoberturaLida::cobertura), declarados, cobertura.map(CoberturaLida::natureza));
    }

    // Método auxiliar que recusa a carga quando um código cita anexo que o anexos-declarados.csv não declara, ou quando cita anexo e o arquivo não veio (D9). Só confere quando os arquivos foram lidos sem recusa, para não acusar efeito de outro problema. Substituiu em 01/10/2026 a checagem contra item-anexo.csv, de 30/09/2026.
    private static void exigirAnexosAdmitidosDeclarados(
            List<ClassificacaoTributaria> classificacoes,
            List<AnexoDeclarado> declarados,
            boolean arquivoVeio,
            RecusasDaCarga recusas) {
        if (recusas.temRecusaEm(ARQUIVO_CLASSIFICACAO_TRIBUTARIA) || recusas.temRecusaEm(ARQUIVO_ANEXOS_DECLARADOS)) {
            return;
        }
        Set<IdentificadorAnexo> validos = declarados.stream()
                .map(AnexoDeclarado::identificador)
                .collect(Collectors.toSet());
        for (ClassificacaoTributaria classificacao : classificacoes) {
            classificacao.anexosAdmitidos().orElse(Set.of()).stream()
                    .filter(anexo -> !validos.contains(anexo))
                    .map(IdentificadorAnexo::valor)
                    .sorted()
                    .forEach(anexo -> recusas.registrarDoArquivo(ARQUIVO_CLASSIFICACAO_TRIBUTARIA, arquivoVeio
                            ? ("O cClassTrib \"%s\" admite o anexo \"%s\" na coluna \"%s\", mas %s não declara esse "
                                    + "anexo.").formatted(classificacao.codigo().valor(), anexo,
                                    ImportadorClassificacaoTributariaCsv.COLUNA_ANEXOS_ADMITIDOS,
                                    ARQUIVO_ANEXOS_DECLARADOS)
                            : ("O cClassTrib \"%s\" admite o anexo \"%s\" na coluna \"%s\", mas a carga não trouxe "
                                    + "%s, que é a lista dos anexos válidos.").formatted(classificacao.codigo().valor(),
                                    anexo, ImportadorClassificacaoTributariaCsv.COLUNA_ANEXOS_ADMITIDOS,
                                    ARQUIVO_ANEXOS_DECLARADOS)));
        }
    }

    // Método auxiliar que diz se o arquivo veio. Emenda de 04/10/2026 (D024): arquivo que veio fora de UTF-8 veio — a recusa da codificação é registrada por quem o lê.
    private static boolean veio(FontesDoCatalogo fontes, String arquivo) throws IOException {
        try {
            return fontes.abrir(arquivo).map(LeitorDeCatalogoEmCsv::fechar).isPresent();
        } catch (ImportacaoDeCatalogoInvalida foraDeUtf8) {
            return true;
        }
    }

    // Método auxiliar que abre o arquivo pela fonte. Acrescentado em 04/10/2026 (D024): o arquivo fora de UTF-8 entra nas recusas da carga, como qualquer outro problema do arquivo, e a leitura dos outros arquivos continua.
    private static Optional<Optional<Reader>> abrir(
            FontesDoCatalogo fontes, String arquivo, RecusasDaCarga recusas) throws IOException {
        try {
            return Optional.of(fontes.abrir(arquivo));
        } catch (ImportacaoDeCatalogoInvalida foraDeUtf8) {
            recusas.registrar(arquivo, foraDeUtf8);
            return Optional.empty();
        }
    }

    // Método auxiliar que fecha o arquivo aberto só para saber se ele veio.
    private static boolean fechar(Reader aberto) {
        try {
            aberto.close();
        } catch (IOException ignorada) {
            // Fechar um leitor que só foi aberto para teste de presença não tem o que recuperar.
        }
        return true;
    }

    // Representa a leitura de um arquivo de dados por um dos importadores.
    @FunctionalInterface
    private interface LeituraDoImportador<T> {

        // Lê o arquivo aberto e devolve a tabela.
        TabelaImportada<T> ler(Reader origem, String arquivo) throws IOException;
    }

    // Método auxiliar que abre o arquivo e lê a tabela; arquivo que falta é recusado quando é obrigatório.
    private static <T> TabelaImportada<T> lerTabela(
            FontesDoCatalogo fontes,
            String arquivo,
            RecusasDaCarga recusas,
            boolean obrigatorio,
            LeituraDoImportador<T> leitura) throws IOException {

        Optional<Optional<Reader>> legivel = abrir(fontes, arquivo, recusas);
        if (legivel.isEmpty()) {
            return new TabelaImportada<>(List.of(), Optional.empty());
        }
        Optional<Reader> aberto = legivel.get();
        if (aberto.isEmpty()) {
            if (obrigatorio) {
                recusas.registrarDoArquivo(arquivo, motivoDeArquivoAusente(arquivo, fontes));
            }
            return new TabelaImportada<>(List.of(), Optional.empty());
        }
        try (Reader origem = aberto.get()) {
            return leitura.ler(origem, arquivo);
        }
    }

    // Método auxiliar que lê a tabela só se o arquivo veio no envio, e a devolve como substituta.
    private static <T> Optional<TabelaSubstituta<T>> substituta(
            FontesDoCatalogo fontes,
            String arquivo,
            RecusasDaCarga recusas,
            LeituraDoImportador<T> leitura) throws IOException {

        Optional<Reader> aberto = abrir(fontes, arquivo, recusas).orElse(Optional.empty());
        if (aberto.isEmpty()) {
            return Optional.empty();
        }
        try (Reader origem = aberto.get()) {
            TabelaImportada<T> tabela = leitura.ler(origem, arquivo);
            return Optional.of(new TabelaSubstituta<>(tabela.registros(), tabela.natureza()));
        }
    }

    // Método auxiliar que lê o cobertura.csv sem parar no primeiro problema; recusa tabela desconhecida, tabela repetida e tabela sem cobertura declarada.
    // D021 (04/10/2026): a cobertura lida junto com a natureza que o cobertura.csv declara.
    private record CoberturaLida(CoberturaDoCatalogo cobertura, Natureza natureza) {
    }

    // Emenda de 04/10/2026 (D021): o cobertura.csv passou a exigir a coluna natureza, como os demais arquivos de dados, com a mesma regra de uma natureza por arquivo. Até essa data ele não tinha a coluna, e a fonte que ele declara — citada como fundamento dos apontamentos — escapava da faixa de procedência.
    private static Optional<CoberturaLida> lerCobertura(
            FontesDoCatalogo fontes, RecusasDaCarga recusas, boolean obrigatorio) throws IOException {

        Optional<Optional<Reader>> legivel = abrir(fontes, ARQUIVO_COBERTURA, recusas);
        if (legivel.isEmpty()) {
            return Optional.empty();
        }
        Optional<Reader> aberto = legivel.get();
        if (aberto.isEmpty()) {
            if (obrigatorio) {
                recusas.registrarDoArquivo(ARQUIVO_COBERTURA,
                        motivoDeArquivoAusente(ARQUIVO_COBERTURA, fontes));
            }
            return Optional.empty();
        }

        List<LinhaCsv> linhas;
        try (Reader origem = aberto.get()) {
            linhas = LeitorCsv.ler(origem, RecusaDoCatalogoEmCsv.INSTANCIA);
        } catch (ImportacaoDeCatalogoInvalida arquivoMalformado) {
            recusas.registrar(ARQUIVO_COBERTURA, arquivoMalformado);
            return Optional.empty();
        }

        Optional<Natureza> natureza = Optional.empty();
        try {
            natureza = NaturezaEmCsv.uniforme(linhas);
        } catch (ImportacaoDeCatalogoInvalida semNatureza) {
            recusas.registrar(ARQUIVO_COBERTURA, semNatureza);
        }

        Map<String, ProcedenciaNormativa> porTabela = new LinkedHashMap<>();
        for (LinhaCsv linha : linhas) {
            try {
                String tabela = linha.textoObrigatorio(COLUNA_TABELA);
                if (!ehTabelaConhecida(tabela)) {
                    recusas.registrarDaLinha(ARQUIVO_COBERTURA, linha.numero(), Optional.of(COLUNA_TABELA),
                            Optional.of(tabela), "tabela desconhecida \"%s\". Valores aceitos: %s."
                                    .formatted(tabela, Arrays.toString(TabelaNormativa.values())));
                    continue;
                }
                ProcedenciaNormativa procedencia = ProcedenciaEmCsv.ler(linha);
                if (porTabela.put(tabela, procedencia) != null) {
                    recusas.registrarDaLinha(ARQUIVO_COBERTURA, linha.numero(), Optional.of(COLUNA_TABELA),
                            Optional.of(tabela),
                            "a tabela \"%s\" teve cobertura declarada duas vezes.".formatted(tabela));
                }
            } catch (ImportacaoDeCatalogoInvalida | ExcecaoDeDominio recusada) {
                recusas.registrar(ARQUIVO_COBERTURA, recusada);
            }
        }

        List<String> faltando = new ArrayList<>();
        for (TabelaNormativa tabela : TabelaNormativa.values()) {
            if (!porTabela.containsKey(tabela.name())) {
                faltando.add(tabela.name());
            }
        }
        if (!faltando.isEmpty() && !recusas.temRecusaEm(ARQUIVO_COBERTURA)) {
            recusas.registrarDoArquivo(ARQUIVO_COBERTURA,
                    ("%s não declara cobertura para: %s. Sem essa declaração o sistema não distingue "
                            + "registro ausente do catálogo de tabela não carregada, e apontaria com "
                            + "base em silêncio.").formatted(ARQUIVO_COBERTURA, String.join(", ", faltando)));
        }
        if (recusas.temRecusaEm(ARQUIVO_COBERTURA)) {
            return Optional.empty();
        }

        return Optional.of(new CoberturaLida(new CoberturaDoCatalogo(
                porTabela.get(TabelaNormativa.CLASSIFICACAO_TRIBUTARIA.name()),
                porTabela.get(TabelaNormativa.NCM.name()),
                porTabela.get(TabelaNormativa.ITEM_ANEXO.name())), natureza.orElseThrow()));
    }

    // Método auxiliar que confere se o nome é de uma das tabelas conhecidas.
    private static boolean ehTabelaConhecida(String tabela) {
        return Arrays.stream(TabelaNormativa.values())
                .anyMatch(conhecida -> conhecida.name().equals(tabela));
    }

    // Método auxiliar que explica por que arquivo ausente é recusado. Até a Etapa 11 este texto dizia que qualquer tabela podia vir só com o cabeçalho; desde a Etapa 12, as três tabelas com cobertura declarada precisam ter registro, e só a de alíquotas pode vir vazia.
    private static String motivoDeArquivoAusente(String nomeDoArquivo, FontesDoCatalogo fontes) {
        return ("Falta o arquivo \"%s\" em %s. Arquivo ausente não é lido como tabela vazia. Para "
                + "declarar a tabela de alíquotas sem registros, forneça o arquivo apenas com o "
                + "cabeçalho; as três tabelas com cobertura declarada precisam ter registro.")
                .formatted(nomeDoArquivo, fontes.descricao());
    }
}
