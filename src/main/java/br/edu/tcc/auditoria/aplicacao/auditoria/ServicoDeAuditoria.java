package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.ResultadoAvaliacao;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Serviço que audita um lote de documentos, usando o motor de auditoria e registrando os resultados.
public final class ServicoDeAuditoria {

    private final FonteDeLoteDeDocumentos fonte;
    private final ProvedorDeCatalogo provedorDeCatalogo;
    private final RepositorioDaAuditoria repositorio;
    private final MotorAuditoria motor;
    private final ToleranciaDeValor tolerancia;
    private final Clock relogio;

    // Construtor do serviço de auditoria, que recebe as dependências necessárias para realizar a auditoria.
    public ServicoDeAuditoria(
            FonteDeLoteDeDocumentos fonte,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio) {

        this.fonte = exigir(fonte, "a fonte de documentos");
        this.provedorDeCatalogo = exigir(provedorDeCatalogo, "o provedor de catálogo");
        this.repositorio = exigir(repositorio, "o repositório onde gravar o resultado");
        this.motor = exigir(motor, "o motor de auditoria");
        this.tolerancia = exigir(tolerancia, "a tolerância de valor");
        this.relogio = exigir(relogio, "o relógio");
    }

    // Audita um lote de documentos a partir de uma origem, registrando os resultados e arquivos ilegíveis.
    public ResultadoDaAuditoria auditar(Path origem) {
        if (origem == null) {
            throw new AuditoriaInvalida("Não há origem de documentos a auditar.");
        }

        LoteDeDocumentos lote = fonte.abrir(origem);
        if (lote == null) {
            throw new AuditoriaInvalida("A fonte de documentos não devolveu lote para \"%s\".".formatted(origem));
        }

        CatalogoParaAuditoria catalogo = provedorDeCatalogo.carregar();
        if (catalogo == null) {
            throw new AuditoriaInvalida("O provedor de catálogo não devolveu catálogo.");
        }

        ConjuntoRegras conjunto = ConjuntoRegras.padrao(catalogo.cobertura(), tolerancia);
        List<Avaliacao> avaliacoes = motor.auditar(lote.documentos(), catalogo, conjunto);

        List<AchadoLocalizado> achados = localizar(avaliacoes, lote);
        List<Avaliacao.NaoAvaliada> naoAvaliadas = naoConcluidas(avaliacoes);

        ExecucaoAuditoria execucao = ExecucaoAuditoria.de(
                UUID.randomUUID(),
                relogio.instant(),
                lote.hashDaEntrada(),
                catalogo.versao(),
                conjunto.versao(),
                lote.documentos().size(),
                lote.quantidadeDeItens(),
                conjunto.identificadores(),
                achados.stream().map(AchadoLocalizado::achado).toList());

        ResultadoDaAuditoria resultado = new ResultadoDaAuditoria(
                execucao, lote.documentos(), achados, avaliacoes.size(), naoAvaliadas);

        repositorio.persistir(resultado);
        return resultado;
    }
    // Método auxiliar para filtrar avaliações que não foram concluídas, retornando uma lista de avaliações não avaliadas.
    private static List<Avaliacao.NaoAvaliada> naoConcluidas(List<Avaliacao> avaliacoes) {
        return avaliacoes.stream()
                .filter(avaliacao -> avaliacao.resultado() == ResultadoAvaliacao.NAO_AVALIADO)
                .map(avaliacao -> (Avaliacao.NaoAvaliada) avaliacao)
                .toList();
    }

    // Método auxiliar para localizar achados em avaliações, associando cada achado ao seu item correspondente no lote de documentos.
    private static List<AchadoLocalizado> localizar(List<Avaliacao> avaliacoes, LoteDeDocumentos lote) {
        Map<String, ItemDocumento> itensPorChaveEItem = indexar(lote);
        List<AchadoLocalizado> achados = new ArrayList<>();

        for (Avaliacao avaliacao : avaliacoes) {
            if (avaliacao.resultado() != ResultadoAvaliacao.ACHADO) {
                continue;
            }
            Achado achado = avaliacao.achado().orElseThrow(() -> new AuditoriaInvalida(
                    "A avaliação da regra %s diz ter apontamento e não traz nenhum."
                            .formatted(avaliacao.regraId())));

            if (achado.numeroItem().isEmpty()) {
                // A regra apontou sobre o documento inteiro, sem item. Isso não é permitido, pois a gravação de apontamento ainda não existe para documentos inteiros.
                throw new AuditoriaInvalida(
                        ("A regra %s apontou sobre o documento %s inteiro, sem item. A gravação de "
                                + "apontamento de documento ainda não existe: hoje a identidade do "
                                + "apontamento é o resumo do item.")
                                .formatted(achado.regraId(), achado.chaveAcesso().valor()));
            }

            int numeroItem = achado.numeroItem().getAsInt();
            ItemDocumento item = itensPorChaveEItem.get(chaveDe(achado.chaveAcesso().valor(), numeroItem));
            if (item == null) {
                throw new AuditoriaInvalida(
                        ("A regra %s apontou o item %d do documento %s, que não está no lote lido.")
                                .formatted(achado.regraId(), numeroItem, achado.chaveAcesso().valor()));
            }
            achados.add(new AchadoLocalizado(achado, HashDoItem.de(achado.chaveAcesso(), item)));
        }
        return List.copyOf(achados);
    }
    // Método auxiliar para criar um índice de itens de documento a partir do lote de documentos, usando uma chave composta pela chave de acesso do documento e o número do item.
    private static Map<String, ItemDocumento> indexar(LoteDeDocumentos lote) {
        Map<String, ItemDocumento> indice = new LinkedHashMap<>();
        for (DocumentoComItens documento : lote.documentos()) {
            String chaveAcesso = documento.documento().chaveAcesso().valor();
            for (ItemDocumento item : documento.itens()) {
                indice.put(chaveDe(chaveAcesso, item.numeroItem()), item);
            }
        }
        return indice;
    }

    private static String chaveDe(String chaveAcesso, int numeroItem) {
        return chaveAcesso + "#" + numeroItem;
    }

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new AuditoriaInvalida("O serviço de auditoria precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
