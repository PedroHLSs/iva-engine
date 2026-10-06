package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.OrigemDaImportacaoMudou;
import br.edu.tcc.auditoria.aplicacao.catalogo.OrigemDaImportacaoNaoInformada;
import br.edu.tcc.auditoria.aplicacao.catalogo.ResultadoDaImportacao;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas.OrigemVista;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.aplicacao.catalogo.TipoDaImportacao;
import br.edu.tcc.auditoria.infraestrutura.catalogo.FontesDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.ImportacaoDeCatalogoInvalida;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Classe do comando importar-catalogo, que carrega as tabelas normativas dos CSV fornecidos pelo usuário. É o único caminho pelo qual conteúdo normativo entra no sistema.
// Emenda de 04/10/2026 (D026, decisão D-b): depois da primeira carga, a pasta pode trazer só parte dos arquivos; as tabelas que faltam são copiadas da carga mais recente, que a pessoa nomeia em --partir-de, e o resultado é uma carga nova. Sem --partir-de, ou com outra carga, é recusado dizendo qual é a mais recente: a linha de comando não tem tela que mostre a origem antes, e por isso não herda em silêncio. O comando passou a gravar pelo ServicoDeCargas, dentro da trava do acervo, também a importação completa. Até essa data exigia sempre os cinco arquivos e gravava direto pelo ServicoDeImportacaoDeCatalogo.
// "É o único caminho pelo qual conteúdo normativo entra no sistema" valeu até a Etapa 12, quando a importação passou a existir também na web (D013).
@Component
class ComandoImportarCatalogo implements Comando {

    static final String NOME = "importar-catalogo";

    private static final String OPCAO_DIRETORIO = "diretorio";
    private static final String OPCAO_VERSAO = "versao";
    private static final String OPCAO_PARTIR_DE = "partir-de";
    private static final DateTimeFormatter FORMATO_DA_VERSAO_AUTOMATICA =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ServicoDeCargas servico;
    private final Saida saida;
    private final Clock relogio;

    // Construtor que recebe o serviço de cargas, a saída e o relógio. Até 04/10/2026 recebia o ServicoDeImportacaoDeCatalogo.
    ComandoImportarCatalogo(ServicoDeCargas servico, Saida saida, Clock relogio) {
        this.servico = servico;
        this.saida = saida;
        this.relogio = relogio;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Importa as tabelas normativas de um diretório de arquivos CSV.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --diretorio=<caminho> [--versao=<nome>] [--partir-de=<versao>]

                  --diretorio  diretório com os arquivos CSV do catálogo:
                               %s
                               e, opcional, anexos-declarados.csv.
                               A primeira carga exige os cinco. Depois dela, basta
                               um: os que faltarem são copiados da carga indicada
                               em --partir-de, que fica intacta.
                  --versao     nome desta carga, registrado em cada execução de
                               auditoria feita contra ela. Se omitido, é gerado a
                               partir da data e hora.
                  --partir-de  a carga mais recente, de onde vêm as tabelas que a
                               pasta não traz. Precisa ser a mais recente; se não
                               for, o comando diz qual é. Com os cinco arquivos na
                               pasta, nada é herdado e esta opção não é usada.
                """.formatted(NOME, LeitorDeCatalogoEmCsv.arquivosEsperados());
    }

    // Lê os CSV da pasta, grava a carga e mostra quantos registros entraram.
    // Emenda de 04/10/2026 (D026): com a pasta incompleta, a importação é parcial e herda da carga de --partir-de; a saída diz de qual carga, com os dois instantes dela, e quais tabelas vieram da pasta e quais foram herdadas.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_DIRETORIO, OPCAO_VERSAO, OPCAO_PARTIR_DE));

        Path diretorio = argumentos.caminhoObrigatorio(OPCAO_DIRETORIO);
        String versao = argumentos.texto(OPCAO_VERSAO).orElseGet(this::versaoAutomatica);
        Optional<String> partirDe = argumentos.texto(OPCAO_PARTIR_DE);

        ResultadoDaImportacao resultado;
        try {
            if (!Files.isDirectory(diretorio) || LeitorDeCatalogoEmCsv.estaCompleto(FontesDoCatalogo.daPasta(diretorio))) {
                resultado = servico.importarCompleta(LeitorDeCatalogoEmCsv.ler(diretorio, versao), partirDe);
            } else {
                FontesDoCatalogo fontes = FontesDoCatalogo.daPasta(diretorio);
                resultado = servico.importarParcial(versao,
                        () -> lerSemExcecaoVerificada(diretorio, () -> LeitorDeCatalogoEmCsv.lerSubstituicao(fontes)),
                        partirDe.map(OrigemVista::soPelaVersao),
                        versaoLida -> lerSemExcecaoVerificada(diretorio,
                                () -> LeitorDeCatalogoEmCsv.ler(diretorio, versaoLida)));
            }
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Não foi possível ler o catálogo em \"%s\".".formatted(diretorio), erroDeLeitura);
        } catch (OrigemDaImportacaoNaoInformada semOrigem) {
            throw new UsoInvalido(("A pasta \"%s\" não traz %s. As tabelas que faltam seriam copiadas da carga "
                    + "mais recente, \"%s\" (%s). Para isso, repita com --%s=%s. Nada foi gravado.")
                    .formatted(diretorio, String.join(", ", faltando(diretorio)), semOrigem.origemAtual().versao(),
                            instantes(semOrigem.origemAtual()), OPCAO_PARTIR_DE, semOrigem.origemAtual().versao()));
        } catch (OrigemDaImportacaoMudou outraOrigem) {
            throw new ImportacaoDeCatalogoInvalida(("--%s=%s não é a carga mais recente: a mais recente é \"%s\" "
                    + "(%s). Nada foi gravado. Repita com --%s=%s, se é dela que as tabelas que faltam devem vir.")
                    .formatted(OPCAO_PARTIR_DE, partirDe.orElse(""), outraOrigem.origemAtual().versao(),
                            instantes(outraOrigem.origemAtual()), OPCAO_PARTIR_DE, outraOrigem.origemAtual().versao()));
        }

        ResumoDaImportacao resumo = resultado.resumo();
        saida.linha("Catálogo importado como versão \"%s\".", resumo.versao());
        saida.linha("  classificações tributárias: %d", resumo.classificacoesTributarias());
        saida.linha("  NCM: %d", resumo.registrosDeNcm());
        saida.linha("  itens de anexo: %d", resumo.itensDeAnexo());
        saida.linha("  alíquotas: %d", resumo.aliquotas());
        // Emenda de 04/10/2026 (D025): os anexos declarados também entram no resumo e no total.
        saida.linha("  anexos declarados: %d", resumo.anexosDeclarados());
        saida.linha("  total: %d registros", resumo.total());
        if (resultado.tipo() == TipoDaImportacao.PARCIAL) {
            EstadoDaCarga origem = resultado.origem().orElseThrow();
            saida.linha("  a partir de \"%s\" (%s)", origem.versao(), instantes(origem));
            saida.linha("  tabelas da pasta: %s", String.join(", ", resultado.tabelasEnviadas()));
            saida.linha("  tabelas herdadas: %s", String.join(", ", resultado.tabelasHerdadas()));
        } else {
            saida.linha("  nenhuma tabela herdada: importação completa");
        }
        saida.linha(resultado.aviso());
    }

    // Representa uma leitura da pasta, que declara IOException.
    @FunctionalInterface
    private interface Leitura<T> {

        // Lê os arquivos e devolve o que leu.
        T ler() throws IOException;
    }

    // Método auxiliar que roda a leitura dentro de uma operação que não aceita exceção verificada.
    private static <T> T lerSemExcecaoVerificada(Path diretorio, Leitura<T> leitura) {
        try {
            return leitura.ler();
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Não foi possível ler o catálogo em \"%s\".".formatted(diretorio), erroDeLeitura);
        }
    }

    // Método auxiliar que escreve os dois instantes da carga de origem, por extenso e com toda a precisão gravada.
    private static String instantes(EstadoDaCarga carga) {
        return "importada em %s; %s".formatted(carga.importadoEm(),
                carga.alteradaEm().map(Instant::toString).map("alterada em %s"::formatted).orElse("nunca alterada"));
    }

    // Método auxiliar que lista os arquivos obrigatórios que a pasta não traz.
    private static List<String> faltando(Path diretorio) {
        return List.of(LeitorDeCatalogoEmCsv.arquivosEsperados().split(", ")).stream()
                .filter(arquivo -> !Files.isRegularFile(diretorio.resolve(arquivo)))
                .toList();
    }

    // Método auxiliar que cria o nome da versão pela data e hora, quando --versao não é informado.
    private String versaoAutomatica() {
        return "carga-" + FORMATO_DA_VERSAO_AUTOMATICA.format(relogio.instant());
    }
}
