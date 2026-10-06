package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.catalogo.EfeitoDaEdicao;
import br.edu.tcc.auditoria.aplicacao.catalogo.EstadoDaCarga;
import br.edu.tcc.auditoria.aplicacao.catalogo.ResultadoDaEdicao;
import br.edu.tcc.auditoria.aplicacao.catalogo.ResultadoDaImportacao;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas.OrigemVista;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.infraestrutura.catalogo.FontesDoCatalogo;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Controlador de /api/cargas. Listar e consultar: qualquer perfil. Importar, editar e excluir: só administrador, recusado pelo filtro de segurança. Carga já entregue a uma análise está selada: editá-la cria carga nova, e excluí-la é recusado com a quantidade de análises. A edição é por substituição de CSV, nunca por formulário, porque conteúdo normativo só entra por arquivo. Acrescentado na Etapa 12.
// Emenda de 04/10/2026 (D026): depois da primeira carga, importar aceita parte dos arquivos, e as tabelas que não vieram são copiadas da carga mais recente; o pedido diz qual carga a tela mostrou, com os dois instantes dela, e a resposta diz o que foi herdado e de onde. E os seis nomes de arquivo são aceitos, o anexos-declarados.csv inclusive (D-c).
@RestController
@RequestMapping("/api/cargas")
class ControladorDeCargas {

    // Nome do campo do formulário em que os CSV chegam; o nome de cada arquivo diz qual tabela ele é.
    static final String CAMPO_DOS_ARQUIVOS = "arquivos";

    private final ServicoDeCargas cargas;

    // Construtor que recebe o serviço de cargas.
    ControladorDeCargas(ServicoDeCargas cargas) {
        this.cargas = cargas;
    }

    // Representa o resultado da importação, com a quantidade de registros por tabela.
    // Emenda de 04/10/2026 (D025): traz os anexos declarados, que até essa data ficavam fora da resposta e do total.
    // Emenda de 04/10/2026 (D026): diz se a importação foi completa ou parcial, de qual carga as tabelas herdadas vieram, com os dois instantes dela — null com o motivo na completa —, a origem informada que não foi usada, as tabelas enviadas e as herdadas, e o aviso. Até essa data trazia só a versão e as contagens.
    record ImportacaoExposta(String versao, int classificacoesTributarias, int registrosDeNcm,
            int itensDeAnexo, int aliquotas, int anexosDeclarados, int total,
            String tipo, String cargaDeOrigem, String motivoDaOrigemAusente, Instant origemImportadaEm,
            Instant origemAlteradaEm, String motivoDaAlteracaoDaOrigemAusente, String origemInformadaNaoUsada,
            List<String> tabelasEnviadas, List<String> tabelasHerdadas, String aviso) {

        // Método estático que monta a importação exposta a partir do resultado.
        static ImportacaoExposta de(ResultadoDaImportacao resultado) {
            ResumoDaImportacao resumo = resultado.resumo();
            return new ImportacaoExposta(resumo.versao(), resumo.classificacoesTributarias(),
                    resumo.registrosDeNcm(), resumo.itensDeAnexo(), resumo.aliquotas(),
                    resumo.anexosDeclarados(), resumo.total(),
                    resultado.tipo().name(),
                    resultado.origem().map(EstadoDaCarga::versao).orElse(null),
                    resultado.origem().isPresent() ? null : "importação completa: nenhuma tabela herdada",
                    resultado.origem().map(EstadoDaCarga::importadoEm).orElse(null),
                    resultado.origem().flatMap(EstadoDaCarga::alteradaEm).orElse(null),
                    resultado.origem().isEmpty() ? "importação completa: nenhuma tabela herdada"
                            : resultado.origem().get().alteradaEm().isPresent() ? null
                            : "a carga de origem nunca foi alterada depois de importada",
                    resultado.origemInformadaNaoUsada().orElse(null),
                    resultado.tabelasEnviadas(), resultado.tabelasHerdadas(), resultado.aviso());
        }
    }

    // Representa o resultado da edição: o efeito, a carga de origem, a carga resultante e as tabelas substituídas.
    record EdicaoExposta(String efeito, String versaoDeOrigem, String versaoResultante,
            List<String> tabelasSubstituidas, String explicacao) {
    }

    // Lista as cargas, da mais recente para a mais antiga, cada uma com o que uma edição faria.
    @GetMapping
    List<CargaExposta> listar() {
        return cargas.listar().stream().map(this::expor).toList();
    }

    // Devolve uma carga, com a prévia da edição e se ela pode ser excluída.
    @GetMapping("/{versao}")
    CargaExposta consultar(@PathVariable String versao) {
        return expor(cargas.estado(versao));
    }

    // Importa uma carga nova a partir dos cinco CSV, com as mesmas recusas da linha de comando.
    // Emenda de 04/10/2026 (D026): com os cinco arquivos obrigatórios, a importação é completa e não herda nada, e a origem informada, se veio, não é usada. Sem algum deles, é parcial: o pedido diz em cargaDeOrigemEsperada, origemImportadaEmEsperada e origemAlteradaEmEsperada a carga mais recente que a tela mostrou, exatamente como o GET /api/cargas a devolveu — alterada vazia quer dizer "nunca alterada" —, e o servidor confere isso dentro da trava do acervo. No acervo vazio, a parcial é recusada com a mensagem de arquivo ausente de sempre.
    @PostMapping
    ResponseEntity<ImportacaoExposta> importar(
            @RequestParam(value = "versao", required = false) String versao,
            @RequestParam(value = "cargaDeOrigemEsperada", required = false) String cargaDeOrigemEsperada,
            @RequestParam(value = "origemImportadaEmEsperada", required = false) String origemImportadaEmEsperada,
            @RequestParam(value = "origemAlteradaEmEsperada", required = false) String origemAlteradaEmEsperada,
            @RequestParam(value = CAMPO_DOS_ARQUIVOS, required = false) List<MultipartFile> arquivos)
            throws IOException {
        if (versao == null || versao.isBlank()) {
            throw new PedidoInvalido(
                    "Informe a versão da carga no campo \"versao\". Ela é registrada em cada análise feita "
                            + "contra esta carga, e é por ela que a análise é reaberta depois.");
        }
        FontesDoCatalogo fontes = fontes(arquivos);
        Optional<String> origemInformada = Optional.ofNullable(cargaDeOrigemEsperada)
                .map(String::strip).filter(texto -> !texto.isEmpty());

        ResultadoDaImportacao resultado;
        if (LeitorDeCatalogoEmCsv.estaCompleto(fontes)) {
            resultado = cargas.importarCompleta(LeitorDeCatalogoEmCsv.ler(fontes, versao.strip()), origemInformada);
        } else {
            Optional<Instant> importadaEm = instante(origemImportadaEmEsperada, "origemImportadaEmEsperada");
            Optional<Instant> alteradaEm = instante(origemAlteradaEmEsperada, "origemAlteradaEmEsperada");
            resultado = cargas.importarParcial(versao,
                    () -> lerSemExcecaoVerificada(() -> LeitorDeCatalogoEmCsv.lerSubstituicao(fontes)),
                    origemInformada.map(origem -> OrigemVista.pelaTela(origem, importadaEm, alteradaEm)),
                    versaoLida -> lerSemExcecaoVerificada(() -> LeitorDeCatalogoEmCsv.ler(fontes, versaoLida)));
        }
        return ResponseEntity.created(URI.create("/api/cargas/" + codificar(resultado.resumo().versao())))
                .body(ImportacaoExposta.de(resultado));
    }

    // Edita a carga substituindo as tabelas cujos CSV vieram. O pedido diz o efeito que a tela mostrou; se mudou enquanto a pessoa editava, nada é gravado e a resposta traz o efeito novo.
    @PutMapping("/{versao}")
    EdicaoExposta editar(
            @PathVariable String versao,
            @RequestParam(value = "efeitoEsperado", required = false) String efeitoEsperado,
            @RequestParam(value = "versaoNova", required = false) String versaoNova,
            @RequestParam(value = CAMPO_DOS_ARQUIVOS, required = false) List<MultipartFile> arquivos)
            throws IOException {
        EfeitoDaEdicao efeito = Parametros.constante(efeitoEsperado, EfeitoDaEdicao.class, "efeitoEsperado", null);
        if (efeito == null) {
            throw new PedidoInvalido(
                    "Informe em \"efeitoEsperado\" o efeito que a tela mostrou antes de confirmar: "
                            + "ALTERAR_RASCUNHO ou CRIAR_VERSAO_NOVA. Sem ele, o servidor não tem como saber "
                            + "se a pessoa viu o que salvar vai fazer.");
        }
        ResultadoDaEdicao resultado = cargas.editar(versao,
                LeitorDeCatalogoEmCsv.lerSubstituicao(fontes(arquivos)),
                efeito,
                Optional.ofNullable(versaoNova));
        return new EdicaoExposta(resultado.efeito().name(), resultado.versaoDeOrigem(),
                resultado.versaoResultante(), resultado.tabelasSubstituidas(),
                resultado.efeito() == EfeitoDaEdicao.CRIAR_VERSAO_NOVA
                        ? ("A carga \"%s\" ficou intacta. A versão \"%s\" foi criada e passou a ser a usada "
                                + "nas próximas análises.").formatted(resultado.versaoDeOrigem(),
                                resultado.versaoResultante())
                        : "O rascunho \"%s\" foi alterado.".formatted(resultado.versaoResultante()));
    }

    // Exclui um rascunho; carga selada é recusada com a quantidade de análises que dependem dela.
    @DeleteMapping("/{versao}")
    ResponseEntity<Void> excluir(@PathVariable String versao) {
        cargas.excluir(versao);
        return ResponseEntity.noContent().build();
    }

    // Método auxiliar que monta a carga exposta com a prévia e o motivo de não poder excluir.
    private CargaExposta expor(EstadoDaCarga estado) {
        String motivoDeNaoExcluir = null;
        if (estado.selada()) {
            motivoDeNaoExcluir = estado.analisesQueUsam() > 0
                    ? "a carga é usada por %d análise(s) e não pode ser excluída"
                            .formatted(estado.analisesQueUsam())
                    : "a carga foi entregue a uma análise e está selada; relatórios exportados podem citá-la";
        }
        return CargaExposta.de(estado, cargas.previa(estado.versao()), motivoDeNaoExcluir);
    }

    // Método auxiliar que junta os arquivos enviados pelo nome; recusa nome que não é de nenhuma tabela e nome repetido.
    // Emenda de 04/10/2026 (D026, decisão D-c): os nomes aceitos são os seis de arquivosAceitos(), o anexos-declarados.csv inclusive, na importação e na edição. Até essa data eram os cinco de arquivosEsperados(), e o anexos-declarados.csv era recusado como "não é de nenhuma tabela do catálogo".
    private static FontesDoCatalogo fontes(List<MultipartFile> arquivos) throws IOException {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new PedidoInvalido(
                    "Nenhum arquivo foi enviado no campo \"%s\". Os nomes esperados são: %s."
                            .formatted(CAMPO_DOS_ARQUIVOS, LeitorDeCatalogoEmCsv.arquivosAceitos()));
        }
        List<String> esperados = List.of(LeitorDeCatalogoEmCsv.arquivosAceitos().split(", "));
        Map<String, byte[]> porNome = new LinkedHashMap<>();
        for (MultipartFile arquivo : arquivos) {
            String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().strip();
            if (!esperados.contains(nome)) {
                throw new PedidoInvalido(
                        ("O arquivo \"%s\" não é de nenhuma tabela do catálogo. Os nomes esperados são: %s. "
                                + "O nome do arquivo é o que diz qual tabela ele é.")
                                .formatted(nome, LeitorDeCatalogoEmCsv.arquivosAceitos()));
            }
            if (porNome.put(nome, arquivo.getBytes()) != null) {
                throw new PedidoInvalido("O arquivo \"%s\" veio mais de uma vez.".formatted(nome));
            }
        }
        return FontesDoCatalogo.emMemoria(porNome);
    }

    // Representa uma leitura dos arquivos em memória, que declara IOException.
    @FunctionalInterface
    private interface Leitura<T> {

        // Lê os arquivos e devolve o que leu.
        T ler() throws IOException;
    }

    // Método auxiliar que roda a leitura dentro de uma operação que não aceita exceção verificada. Os arquivos já estão em memória; IOException aqui é falha de leitura do próprio envio.
    private static <T> T lerSemExcecaoVerificada(Leitura<T> leitura) {
        try {
            return leitura.ler();
        } catch (IOException falha) {
            throw new UncheckedIOException("Não foi possível ler os arquivos enviados.", falha);
        }
    }

    // Método auxiliar que lê um instante no formato em que o GET /api/cargas o devolve; vazio é ausência, e texto que não é instante é recusado.
    private static Optional<Instant> instante(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(texto.strip()));
        } catch (DateTimeParseException naoEhInstante) {
            throw new PedidoInvalido(
                    ("O campo \"%s\" deve trazer o instante exatamente como o GET /api/cargas o devolveu, "
                            + "por exemplo 1900-01-01T00:00:00Z; veio \"%s\".").formatted(campo, texto));
        }
    }

    // Método auxiliar que codifica a versão para ir na URL.
    private static String codificar(String versao) {
        return URLEncoder.encode(versao, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
