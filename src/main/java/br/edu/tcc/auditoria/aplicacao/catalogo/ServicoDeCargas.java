package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

// Serviço que lista, importa, edita e exclui cargas de catálogo. A regra central: carga que já foi entregue a uma análise está selada e não muda. Editá-la cria uma carga nova, e excluí-la é recusado. Rascunho, que nunca foi usado, muda e sai livremente.
// Emenda de 04/10/2026 (D026): ganhou a importação parcial — depois da primeira carga, um arquivo basta, e as tabelas que não vieram são copiadas da carga mais recente, que a tela mostrou e o pedido confirma. Todo caminho que muda qual é a carga mais recente, ou o conteúdo de uma possível origem, passou a rodar dentro da trava do acervo, antes da trava da linha: importar, importar em parte, editar nos dois efeitos e excluir. E editar passou a recusar a declaração herdada sobre tabela trocada (GuardaDeDeclaracaoHerdada). Até essa data, importar exigia sempre os cinco arquivos, e só editar e excluir travavam, e só a linha.
public final class ServicoDeCargas {

    // A ordem em que as tabelas da carga são escritas nas respostas, a mesma de SubstituicaoDeTabelas.tabelasSubstituidas.
    private static final List<String> TABELAS_DA_CARGA = List.of(
            NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS, NaturezaDaCarga.REGISTROS_DE_NCM,
            NaturezaDaCarga.ITENS_DE_ANEXO, NaturezaDaCarga.ALIQUOTAS, NaturezaDaCarga.COBERTURA,
            NaturezaDaCarga.ANEXOS_DECLARADOS);

    // Representa a carga de origem que quem pediu a importação parcial viu: a versão e, quando houve tela, os dois instantes que ela mostrou. A versão sozinha não pega a carga alterada no lugar nem a excluída e importada de novo com o mesmo nome. Acrescentado em 04/10/2026 (D026).
    public record OrigemVista(String versao, Optional<InstantesVistos> instantes) {

        // Valida que a versão venha e que os instantes, quando ausentes, venham como Optional vazio.
        public OrigemVista {
            if (versao == null || versao.isBlank() || instantes == null) {
                throw new CatalogoInvalido(
                        "A origem vista precisa da versão; instantes ausentes são Optional.empty().");
            }
            versao = versao.strip();
        }

        // Método estático da origem vista pela tela, que conhece os dois instantes; importada vazia quer dizer que o pedido não a trouxe.
        public static OrigemVista pelaTela(String versao, Optional<Instant> importadaEm, Optional<Instant> alteradaEm) {
            return new OrigemVista(versao, Optional.of(new InstantesVistos(importadaEm, alteradaEm)));
        }

        // Método estático da origem informada só pela versão, na linha de comando, que não tem tela.
        public static OrigemVista soPelaVersao(String versao) {
            return new OrigemVista(versao, Optional.empty());
        }
    }

    // Representa os instantes que a tela mostrou da origem: quando ela foi importada e quando foi alterada pela última vez, vazio se nunca foi.
    public record InstantesVistos(Optional<Instant> importadaEm, Optional<Instant> alteradaEm) {

        // Valida que nenhum dos dois venha nulo.
        public InstantesVistos {
            if (importadaEm == null || alteradaEm == null) {
                throw new CatalogoInvalido("Instante ausente se representa com Optional.empty(), nunca com nulo.");
            }
        }
    }

    private static final DateTimeFormatter FORMATO_DA_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AcervoDeCargas acervo;
    private final ServicoDeImportacaoDeCatalogo importacao;

    // Construtor que recebe o acervo de cargas e o serviço de importação que já grava cargas novas.
    public ServicoDeCargas(AcervoDeCargas acervo, ServicoDeImportacaoDeCatalogo importacao) {
        if (acervo == null || importacao == null) {
            throw new CatalogoInvalido("O serviço de cargas precisa do acervo e do serviço de importação.");
        }
        this.acervo = acervo;
        this.importacao = importacao;
    }

    // Lista as cargas, da mais recente para a mais antiga.
    public List<EstadoDaCarga> listar() {
        return acervo.listar();
    }

    // Devolve o estado de uma carga; recusa versão que não existe.
    public EstadoDaCarga estado(String versao) {
        return acervo.estado(exigirVersao(versao)).orElseThrow(() -> naoEncontrada(versao));
    }

    // Importa uma carga nova, com a mesma validação de sempre mais a guarda de cobertura sobre tabela vazia.
    // Emenda de 04/10/2026 (D026): passa por importarCompleta, e por isso roda dentro da trava do acervo.
    public ResumoDaImportacao importar(CargaDeCatalogo carga) {
        return importarCompleta(carga, Optional.empty()).resumo();
    }

    // Importa uma carga com os cinco arquivos obrigatórios, dentro da trava do acervo. Não herda nada, nem o anexos-declarados.csv que não veio; a origem informada, se veio, não é usada, e o resultado diz isso. Acrescentado em 04/10/2026 (D026).
    public ResultadoDaImportacao importarCompleta(CargaDeCatalogo carga, Optional<String> origemInformada) {
        if (carga == null || origemInformada == null) {
            throw new CatalogoInvalido("Não há carga de catálogo a importar.");
        }
        Optional<String> naoUsada = origemInformada.map(String::strip).filter(texto -> !texto.isEmpty());
        return acervo.comAcervoTravado(maisRecente -> {
            GuardaDeCoberturaSobreTabelaVazia.exigir(carga);
            ResumoDaImportacao resumo = importacao.importar(carga);
            List<String> enviadas = new ArrayList<>(TABELAS_DA_CARGA);
            if (resumo.anexosDeclarados() == 0) {
                enviadas.remove(NaturezaDaCarga.ANEXOS_DECLARADOS);
            }
            String aviso = "Importação completa: nenhuma tabela foi herdada"
                    + naoUsada.map(" — a origem informada, \"%s\", não foi usada"::formatted).orElse("")
                    + ". A carga \"%s\" passou a ser a mais recente, usada nas próximas análises."
                            .formatted(resumo.versao())
                    + (resumo.anexosDeclarados() == 0
                            ? " O anexos-declarados.csv não veio, e numa importação completa ele nunca é "
                                    + "herdado: a carga ficou sem lista de anexos."
                            : "");
            return new ResultadoDaImportacao(TipoDaImportacao.COMPLETA, resumo, Optional.empty(), naoUsada,
                    enviadas, List.of(), aviso);
        });
    }

    // Importa uma carga com só parte dos arquivos: as tabelas que não vieram são copiadas da carga mais recente, que fica intacta, e o resultado é uma carga nova, com a versão informada e derivada_de gravado. Acrescentado em 04/10/2026 (D026).
    // Dentro da trava do acervo, nesta ordem: acervo vazio volta a exigir os cinco arquivos, com a recusa de sempre; sem origem informada, ou sem o instante de importação que a tela mostrou, recusa; origem diferente da mais recente, ou com outros instantes, recusa sem gravar; versão repetida recusa; e a carga montada passa pelas mesmas guardas de uma importação completa.
    // A primeira conferência do acervo vazio, fora da trava, só existe para a recusa ser a de sempre também quando o arquivo enviado tem erro; a decisão que vale é a de dentro da trava.
    public ResultadoDaImportacao importarParcial(
            String versao,
            Supplier<SubstituicaoDeTabelas> leituraParcial,
            Optional<OrigemVista> origemVista,
            Function<String, CargaDeCatalogo> leituraCompleta) {

        String versaoNova = exigirVersao(versao).strip();
        if (leituraParcial == null || origemVista == null || leituraCompleta == null) {
            throw new CatalogoInvalido(
                    "A importação parcial precisa das tabelas, da origem vista e da leitura completa.");
        }
        if (acervo.listar().isEmpty()) {
            exigirOsCinco(versaoNova, leituraCompleta);
        }
        SubstituicaoDeTabelas substituicao = leituraParcial.get();

        return acervo.comAcervoTravado(maisRecente -> {
            if (maisRecente.isEmpty()) {
                exigirOsCinco(versaoNova, leituraCompleta);
            }
            EstadoDaCarga atual = maisRecente.orElseThrow();
            OrigemVista vista = origemVista.orElseThrow(() -> new OrigemDaImportacaoNaoInformada(
                    ("O envio não trouxe os cinco arquivos obrigatórios, e as tabelas que faltam seriam "
                            + "copiadas da carga mais recente, \"%s\", mas o pedido não disse de qual carga "
                            + "herdar. Nada foi gravado. Confira de onde as tabelas viriam e envie de novo "
                            + "informando a origem.").formatted(atual.versao()),
                    atual));
            conferirOrigem(vista, atual);

            return acervo.comCargaTravada(atual.versao(), origemTravada -> {
                if (acervo.existeVersao(versaoNova)) {
                    throw new CatalogoInvalido(
                            ("Já existe carga de catálogo com a versão \"%s\". Escolha outra versão: duas "
                                    + "cargas com o mesmo nome tornariam impossível dizer contra qual delas um "
                                    + "relatório antigo foi produzido. Nada foi gravado.").formatted(versaoNova));
                }
                CargaDeCatalogo origem = acervo.conteudo(origemTravada.versao())
                        .orElseThrow(() -> naoEncontrada(origemTravada.versao()));
                GuardaDeDeclaracaoHerdada.exigir(substituicao, origem);
                CargaDeCatalogo nova = substituicao.aplicarSobre(origem, versaoNova);
                GuardaDeCoberturaSobreTabelaVazia.exigir(nova);
                acervo.salvarDerivada(nova, origemTravada.versao());

                List<String> enviadas = substituicao.tabelasSubstituidas();
                List<String> herdadas = TABELAS_DA_CARGA.stream()
                        .filter(tabela -> !enviadas.contains(tabela)).toList();
                ResumoDaImportacao resumo = new ResumoDaImportacao(nova.versao(),
                        nova.classificacoesTributarias().size(), nova.registrosDeNcm().size(),
                        nova.itensDeAnexo().size(), nova.aliquotas().size(),
                        nova.cobertura().anexosDeclarados().size());
                String aviso = ("As tabelas %s vieram da carga \"%s\", com a natureza que têm lá, e a carga "
                        + "\"%s\" não mudou. Vieram no envio: %s. A versão \"%s\" passou a ser a carga mais "
                        + "recente, usada nas próximas análises.").formatted(String.join(", ", herdadas),
                        origemTravada.versao(), origemTravada.versao(), String.join(", ", enviadas), versaoNova);
                return new ResultadoDaImportacao(TipoDaImportacao.PARCIAL, resumo,
                        Optional.of(origemTravada), Optional.empty(), enviadas, herdadas, aviso);
            });
        });
    }

    // Diz, antes de a pessoa confirmar, o que salvar uma edição desta carga vai fazer.
    public PreviaDaEdicao previa(String versao) {
        return previaDe(estado(versao));
    }

    // Aplica a edição. Confere dentro da trava se o efeito ainda é o que a tela mostrou; se mudou, recusa sem gravar nada e devolve a prévia nova.
    public ResultadoDaEdicao editar(
            String versao,
            SubstituicaoDeTabelas substituicao,
            EfeitoDaEdicao efeitoEsperado,
            Optional<String> versaoNovaEsperada) {

        if (substituicao == null || efeitoEsperado == null || versaoNovaEsperada == null) {
            throw new CatalogoInvalido(
                    "A edição precisa das tabelas novas e do efeito que a tela mostrou antes de confirmar.");
        }
        // Emenda de 04/10/2026 (D026): a trava do acervo vem antes da trava da linha, nos dois efeitos.
        return acervo.comAcervoTravado(maisRecente -> acervo.comCargaTravada(exigirVersao(versao), estado -> {
            PreviaDaEdicao atual = previaDe(estado);
            if (atual.efeito() != efeitoEsperado) {
                throw new EdicaoDesatualizada(
                        ("O efeito de salvar mudou enquanto a edição estava aberta: a tela mostrou %s, e "
                                + "agora é %s. Nada foi gravado. %s")
                                .formatted(efeitoEsperado, atual.efeito(), atual.aviso()),
                        atual);
            }

            CargaDeCatalogo origem = acervo.conteudo(estado.versao())
                    .orElseThrow(() -> naoEncontrada(estado.versao()));

            if (atual.efeito() == EfeitoDaEdicao.ALTERAR_RASCUNHO) {
                // Emenda de 04/10/2026 (D026, decisão D-a): a declaração herdada sobre tabela trocada é recusada antes de montar, nos dois efeitos.
                GuardaDeDeclaracaoHerdada.exigir(substituicao, origem);
                CargaDeCatalogo nova = substituicao.aplicarSobre(origem, estado.versao());
                GuardaDeCoberturaSobreTabelaVazia.exigir(nova);
                acervo.substituirConteudo(estado.versao(), nova);
                return new ResultadoDaEdicao(atual.efeito(), estado.versao(), estado.versao(),
                        substituicao.tabelasSubstituidas());
            }

            String versaoNova = versaoNovaEsperada.map(String::strip).filter(texto -> !texto.isEmpty())
                    .orElseThrow(() -> new EdicaoDesatualizada(
                            ("A carga \"%s\" está selada, e salvar cria uma versão nova, mas o pedido não "
                                    + "disse qual. Nada foi gravado. %s")
                                    .formatted(estado.versao(), atual.aviso()),
                            atual));
            if (acervo.existeVersao(versaoNova)) {
                throw new EdicaoDesatualizada(
                        ("Já existe carga com a versão \"%s\"; outra pessoa pode tê-la criado enquanto a "
                                + "edição estava aberta. Nada foi gravado. %s")
                                .formatted(versaoNova, atual.aviso()),
                        atual);
            }
            GuardaDeDeclaracaoHerdada.exigir(substituicao, origem);
            CargaDeCatalogo nova = substituicao.aplicarSobre(origem, versaoNova);
            GuardaDeCoberturaSobreTabelaVazia.exigir(nova);
            acervo.salvarDerivada(nova, estado.versao());
            return new ResultadoDaEdicao(atual.efeito(), estado.versao(), versaoNova,
                    substituicao.tabelasSubstituidas());
        }));
    }

    // Exclui um rascunho. Carga selada é recusada, com a quantidade de análises que dependem dela.
    // Emenda de 04/10/2026 (D026): a trava do acervo vem antes da trava da linha. Excluir a mais recente muda qual carga a próxima importação parcial herda.
    public void excluir(String versao) {
        acervo.comAcervoTravado(maisRecente -> acervo.comCargaTravada(exigirVersao(versao), estado -> {
            if (estado.selada()) {
                throw new CargaSelada(motivoDeNaoExcluir(estado));
            }
            acervo.excluir(estado.versao());
            return null;
        }));
    }

    // Método auxiliar que, no acervo vazio, roda a leitura completa, que recusa com os arquivos que faltam. Se ela não recusasse, a importação não seria parcial.
    private static void exigirOsCinco(String versao, Function<String, CargaDeCatalogo> leituraCompleta) {
        leituraCompleta.apply(versao);
        throw new CatalogoInvalido(
                "O acervo não tem nenhuma carga, e a primeira importação exige os cinco arquivos obrigatórios.");
    }

    // Método auxiliar que confere se a origem vista ainda é a mais recente, com os mesmos instantes; se não for, recusa sem gravar e leva a origem atual.
    private static void conferirOrigem(OrigemVista vista, EstadoDaCarga atual) {
        if (!vista.versao().equals(atual.versao())) {
            throw new OrigemDaImportacaoMudou(
                    ("A tela mostrou \"%s\" como a carga mais recente, mas agora a mais recente é \"%s\". "
                            + "Nada foi gravado. As tabelas que não vieram seriam copiadas de \"%s\"; confira "
                            + "e envie de novo.").formatted(vista.versao(), atual.versao(), atual.versao()),
                    atual);
        }
        if (vista.instantes().isEmpty()) {
            return;
        }
        InstantesVistos instantes = vista.instantes().get();
        Instant importadaEm = instantes.importadaEm().orElseThrow(() -> new OrigemDaImportacaoNaoInformada(
                ("O pedido diz a origem \"%s\", mas não o instante de importação que a tela mostrou dela. "
                        + "Sem ele não há como saber se é a mesma carga, ou outra importada depois com o mesmo "
                        + "nome. Nada foi gravado.").formatted(atual.versao()),
                atual));
        if (!importadaEm.equals(atual.importadoEm())) {
            throw new OrigemDaImportacaoMudou(
                    ("A carga \"%s\" que a tela mostrou foi importada em %s, e a que existe agora com esse "
                            + "nome foi importada em %s: ela foi excluída e importada de novo. Nada foi gravado. "
                            + "Confira as tabelas que viriam dela e envie de novo.")
                            .formatted(atual.versao(), importadaEm, atual.importadoEm()),
                    atual);
        }
        if (!instantes.alteradaEm().equals(atual.alteradaEm())) {
            throw new OrigemDaImportacaoMudou(
                    ("A carga \"%s\" foi alterada depois que a tela a mostrou (a tela viu %s; agora, %s). "
                            + "Nada foi gravado. Confira as tabelas que viriam dela e envie de novo.")
                            .formatted(atual.versao(),
                                    instantes.alteradaEm().map(Instant::toString).orElse("nunca alterada"),
                                    atual.alteradaEm().map(Instant::toString).orElse("nunca alterada")),
                    atual);
        }
    }

    // Método auxiliar que monta a prévia a partir do estado.
    private PreviaDaEdicao previaDe(EstadoDaCarga estado) {
        if (estado.efeitoDaEdicao() == EfeitoDaEdicao.ALTERAR_RASCUNHO) {
            String aviso = ("A carga \"%s\" ainda não foi usada por nenhuma análise. Salvar altera a "
                    + "própria carga \"%s\", sem criar versão nova.").formatted(estado.versao(), estado.versao())
                    + (estado.maisRecente()
                            ? " Ela é a carga mais recente, e é ela que a próxima análise vai usar."
                            : "");
            return new PreviaDaEdicao(estado.versao(), EfeitoDaEdicao.ALTERAR_RASCUNHO,
                    estado.analisesQueUsam(), Optional.empty(), estado.maisRecente(), aviso);
        }

        String versaoSugerida = sugerirVersao(estado.versao());
        String uso = estado.analisesQueUsam() > 0
                ? "A carga \"%s\" é usada por %d análise(s)".formatted(estado.versao(), estado.analisesQueUsam())
                : ("A carga \"%s\" foi entregue a uma análise em %s e está selada; nenhuma análise gravada "
                        + "depende dela hoje, mas relatórios exportados podem citá-la")
                        .formatted(estado.versao(), FORMATO_DA_DATA.format(estado.seladaEm().orElseThrow()));
        String aviso = ("%s. Salvar criará a versão \"%s\" com as tabelas enviadas, e a carga \"%s\" "
                + "continuará intacta. A versão \"%s\" passará a ser a carga mais recente, usada nas "
                + "próximas análises.").formatted(uso, versaoSugerida, estado.versao(), versaoSugerida);
        return new PreviaDaEdicao(estado.versao(), EfeitoDaEdicao.CRIAR_VERSAO_NOVA,
                estado.analisesQueUsam(), Optional.of(versaoSugerida), true, aviso);
    }

    // Método auxiliar que sugere o nome da versão nova: a de origem com "-ed" e o primeiro número livre.
    private String sugerirVersao(String origem) {
        int numero = 1;
        while (acervo.existeVersao(origem + "-ed" + numero)) {
            numero++;
        }
        return origem + "-ed" + numero;
    }

    // Método auxiliar que explica por que a carga selada não é excluída.
    private static String motivoDeNaoExcluir(EstadoDaCarga estado) {
        if (estado.analisesQueUsam() > 0) {
            return ("A carga \"%s\" é usada por %d análise(s) e não pode ser excluída. Ela é o fundamento "
                    + "normativo que essas análises citam, e cada uma é reaberta com a carga que usou.")
                    .formatted(estado.versao(), estado.analisesQueUsam());
        }
        Instant selo = estado.seladaEm().orElseThrow();
        return ("A carga \"%s\" foi entregue a uma análise em %s e está selada. Nenhuma análise gravada "
                + "depende dela hoje, mas relatórios exportados podem citá-la. Por isso ela não é "
                + "excluída.").formatted(estado.versao(), FORMATO_DA_DATA.format(selo));
    }

    // Método auxiliar que confere se a versão foi informada.
    private static String exigirVersao(String versao) {
        if (versao == null || versao.isBlank()) {
            throw new CatalogoInvalido("Não foi informada a versão da carga.");
        }
        return versao;
    }

    // Método auxiliar que monta a recusa de carga inexistente.
    private static CargaNaoEncontrada naoEncontrada(String versao) {
        return new CargaNaoEncontrada("Não há carga de catálogo com a versão \"%s\".".formatted(versao));
    }
}
