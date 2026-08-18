package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

/**
 * Uma verificação de coerência aplicável a um item de documento fiscal.
 *
 * <h2>Regra de ouro</h2>
 *
 * <p><strong>Se o catálogo não tiver dado suficiente para julgar o item, o
 * resultado é {@code NAO_AVALIADO} com motivo, jamais {@code CONFORME}.</strong>
 * Ausência de dado e conformidade não podem se parecer na saída. Uma regra que
 * devolvesse {@code CONFORME} porque a tabela normativa não foi carregada
 * afirmaria, num relatório de auditoria, que conferiu algo que não conferiu — e
 * o texto sairia plausível. Cada regra deste pacote tem teste dedicado a esse
 * caso.</p>
 *
 * <h2>Contrato</h2>
 *
 * <ul>
 *   <li>{@link #avaliar} é uma função pura: mesmos argumentos, mesma resposta.
 *       Não guarda estado entre chamadas, não lê relógio — a data que vale é a
 *       do documento, já fixada dentro do {@link ContextoNormativo} — e não
 *       escreve em lugar nenhum.</li>
 *   <li>A regra nunca lança exceção para dizer que faltou dado. Faltar dado é
 *       resultado, não erro.</li>
 *   <li>{@link #versao()} sobe quando a lógica muda, e obriga a subir também a
 *       versão do {@link ConjuntoRegras} que a contém. Sem isso dois relatórios
 *       com a mesma versão poderiam discordar sobre o mesmo documento.</li>
 * </ul>
 *
 * <p>Nenhuma implementação escreve valor normativo em código. Códigos, pares
 * admitidos, vínculos com anexo, percentuais e datas vêm todos do
 * {@link ContextoNormativo}, que por sua vez vem de importação em tempo de
 * execução. O que pertence à regra é a <em>pergunta</em>; a resposta é do
 * catálogo.</p>
 */
public interface RegraAuditoria {

    /** Identificação estável da regra, usada no relatório e no achado. */
    String id();

    /** Versão da lógica desta regra. */
    String versao();

    /** Gravidade dos achados que esta regra produz. */
    Severidade severidade();

    /**
     * Aplica a regra a um item.
     *
     * @param item      item avaliado, com os campos exatamente como declarados
     * @param documento documento a que o item pertence
     * @param contexto  catálogo já resolvido na data de emissão do documento
     * @return o desfecho, sempre identificado e nunca ambíguo
     */
    Avaliacao avaliar(ItemDocumento item, Documento documento, ContextoNormativo contexto);
}
