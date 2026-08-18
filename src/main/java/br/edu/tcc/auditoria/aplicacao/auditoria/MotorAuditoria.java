package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.RegraAuditoria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Aplica um conjunto de regras a documentos e devolve todas as avaliações.
 *
 * <p>Não decide nada sobre fiscalidade: percorre, chama e junta. Toda conclusão
 * é das regras, e todo dado normativo é do contexto.</p>
 *
 * <h2>Ordem determinística</h2>
 *
 * <p>O mesmo conjunto de entrada produz a mesma lista, na mesma ordem, sempre. A
 * saída é ordenada por:</p>
 *
 * <ol>
 *   <li>chave de acesso do documento, em ordem crescente;</li>
 *   <li>número do item, em ordem crescente;</li>
 *   <li>posição da regra no {@link ConjuntoRegras}.</li>
 * </ol>
 *
 * <p>Ordenar por chave, e não simplesmente percorrer a lista recebida, torna a
 * saída independente da ordem em que os documentos chegaram — o que importa
 * porque essa ordem costuma vir de listagem de diretório ou de consulta a banco,
 * e nenhuma das duas promete estabilidade. Dois relatórios do mesmo lote têm de
 * poder ser comparados linha a linha; se a ordem variasse, toda comparação
 * acusaria diferença onde não há.</p>
 *
 * <p>As ordenações são estáveis: documentos com a mesma chave, ou itens com o
 * mesmo número, mantêm entre si a ordem de entrada, em vez de trocarem de lugar
 * a cada execução.</p>
 *
 * <h2>Nada é descartado</h2>
 *
 * <p>Toda regra é aplicada a todo item, e toda avaliação entra na lista,
 * inclusive as conformes e as não avaliadas. Filtrar aqui — devolver só os
 * achados, por exemplo — faria o relatório perder a informação de quantas regras
 * não puderam ser aplicadas, que é justamente o que distingue uma auditoria
 * honesta de uma auditoria que parece limpa.</p>
 */
public final class MotorAuditoria {

    /**
     * Audita um documento contra o catálogo já resolvido na data dele.
     *
     * @param documento documento e seus itens
     * @param contexto  catálogo resolvido na data de emissão deste documento
     * @param conjunto  regras a aplicar, na ordem em que estão declaradas
     */
    public List<Avaliacao> auditar(
            DocumentoComItens documento, ContextoNormativo contexto, ConjuntoRegras conjunto) {

        exigir(documento, "Não há documento a auditar.");
        exigir(contexto, "Não há contexto normativo: o catálogo precisa ser resolvido na data do documento.");
        exigir(conjunto, "Não há conjunto de regras a aplicar.");

        List<Avaliacao> avaliacoes = new ArrayList<>();
        for (ItemDocumento item : documento.itensOrdenados()) {
            for (RegraAuditoria regra : conjunto.regras()) {
                avaliacoes.add(regra.avaliar(item, documento.documento(), contexto));
            }
        }
        return List.copyOf(avaliacoes);
    }

    /**
     * Audita um lote, construindo um contexto por documento.
     *
     * <p>O contexto vem de {@link ProvedorDeContextoNormativo} e não como
     * argumento único porque documentos de datas diferentes não podem
     * compartilhar catálogo resolvido — ver D003.</p>
     */
    public List<Avaliacao> auditar(
            List<DocumentoComItens> documentos,
            ProvedorDeContextoNormativo provedor,
            ConjuntoRegras conjunto) {

        exigir(documentos, "Não há documentos a auditar.");
        exigir(provedor, "Não há provedor de contexto normativo.");
        exigir(conjunto, "Não há conjunto de regras a aplicar.");
        if (documentos.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de documentos não pode conter elemento nulo.");
        }

        List<Avaliacao> avaliacoes = new ArrayList<>();
        for (DocumentoComItens documento : ordenadosPorChave(documentos)) {
            ContextoNormativo contexto = provedor.contextoPara(documento.documento());
            if (contexto == null) {
                throw new AuditoriaInvalida(
                        "O provedor não construiu contexto normativo para o documento emitido em %s."
                                .formatted(documento.documento().dataEmissao()));
            }
            avaliacoes.addAll(auditar(documento, contexto, conjunto));
        }
        return List.copyOf(avaliacoes);
    }

    private static List<DocumentoComItens> ordenadosPorChave(List<DocumentoComItens> documentos) {
        return documentos.stream()
                .sorted(Comparator.comparing(documento -> documento.documento().chaveAcesso().valor()))
                .toList();
    }

    private static void exigir(Object valor, String mensagem) {
        if (valor == null) {
            throw new AuditoriaInvalida(mensagem);
        }
    }
}
