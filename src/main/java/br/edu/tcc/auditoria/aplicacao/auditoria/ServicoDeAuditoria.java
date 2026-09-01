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

/**
 * Caso de uso central: auditar um lote de documentos e gravar o resultado.
 *
 * <p>A sequência é fixa e cada passo existe por um motivo:</p>
 *
 * <ol>
 *   <li><strong>Ler a origem</strong> — diretório ou ZIP — obtendo documentos de
 *       domínio já normalizados e pseudonimizados, mais o resumo do conjunto de
 *       arquivos que entrou.</li>
 *   <li><strong>Carregar o catálogo</strong> gravado, com a cobertura que a carga
 *       declarou.</li>
 *   <li><strong>Montar o conjunto de regras</strong> com essa cobertura e com a
 *       tolerância de valor configurada. A cobertura entra aqui porque é ela que
 *       decide se o silêncio do catálogo vira apontamento ou vira não
 *       avaliado.</li>
 *   <li><strong>Rodar o motor</strong>, que monta o contexto normativo de cada
 *       documento na data de emissão <em>daquele</em> documento.</li>
 *   <li><strong>Localizar cada apontamento</strong> no item que o originou, para
 *       calcular o resumo que liga o apontamento à sua tratativa.</li>
 *   <li><strong>Gravar</strong> documentos, itens, apontamentos e o recibo da
 *       execução, numa só operação.</li>
 * </ol>
 *
 * <p>Este serviço não filtra nada e não decide nada sobre os apontamentos. Ele
 * também não aplica tratativa: tratativa é decisão humana registrada depois, e
 * consultá-la é assunto de quem lista apontamentos.</p>
 */
public final class ServicoDeAuditoria {

    private final FonteDeLoteDeDocumentos fonte;
    private final ProvedorDeCatalogo provedorDeCatalogo;
    private final RepositorioDaAuditoria repositorio;
    private final MotorAuditoria motor;
    private final ToleranciaDeValor tolerancia;
    private final Clock relogio;

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

    /** Audita tudo o que houver na origem indicada e grava o resultado. */
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

    /**
     * Recolhe as avaliações que não concluíram, com o motivo de cada uma.
     *
     * <p>Os motivos vão para o papel de trabalho. Guardar só a contagem faria o
     * relatório dizer "trinta não avaliadas" sem dizer se faltou campo no
     * documento ou tabela no catálogo — que são problemas de quem lê o relatório
     * e de quem carrega o catálogo, respectivamente.</p>
     */
    private static List<Avaliacao.NaoAvaliada> naoConcluidas(List<Avaliacao> avaliacoes) {
        return avaliacoes.stream()
                .filter(avaliacao -> avaliacao.resultado() == ResultadoAvaliacao.NAO_AVALIADO)
                .map(avaliacao -> (Avaliacao.NaoAvaliada) avaliacao)
                .toList();
    }

    /**
     * Liga cada apontamento ao item que o originou e calcula o resumo do item.
     *
     * <p>O motor devolve as avaliações numa lista plana, identificadas por chave
     * de acesso e número de item. O índice abaixo reencontra o item para que o
     * resumo seja calculado sobre o conteúdo declarado, e não sobre o que o
     * apontamento por acaso citou.</p>
     */
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
                // Nenhuma regra do conjunto atual aponta sobre o documento inteiro:
                // o motor percorre itens. Se isso mudar, a gravação precisa de uma
                // identidade própria para apontamento de documento, e a falha aqui
                // é explícita em vez de virar violação de restrição no banco.
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
