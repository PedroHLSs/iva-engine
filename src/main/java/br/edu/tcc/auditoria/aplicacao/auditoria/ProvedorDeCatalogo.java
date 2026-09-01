package br.edu.tcc.auditoria.aplicacao.auditoria;

/**
 * Porta de carregamento do catálogo normativo gravado.
 *
 * <p>Separada de {@code RepositorioDeCargaDeCatalogo} de propósito: importar e
 * consultar são operações com necessidades diferentes. A importação lida com
 * registros soltos que vieram de CSV; a auditoria precisa do catálogo já
 * indexado e capaz de responder por data.</p>
 */
public interface ProvedorDeCatalogo {

    /**
     * Carrega a carga de catálogo mais recente.
     *
     * @throws br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido se nenhum
     *         catálogo foi importado ainda — auditar sem catálogo produziria um
     *         relatório de não avaliados com aparência de auditoria feita
     */
    CatalogoParaAuditoria carregar();
}
