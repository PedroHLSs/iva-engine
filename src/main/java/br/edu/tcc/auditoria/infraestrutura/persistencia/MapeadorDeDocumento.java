package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Cfop;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Tradução do documento e dos itens do domínio para as linhas gravadas.
 *
 * <p><strong>Optional vazio vira nulo, e nunca zero nem string vazia.</strong>
 * A regra vale nos dois sentidos e é a razão de este mapeamento ser escrito à
 * mão: um mapeador automático que "convenientemente" trocasse ausência por
 * valor neutro apagaria a diferença entre campo não declarado e campo declarado
 * como zero, que é a distinção sobre a qual todo o sistema é construído.</p>
 */
final class MapeadorDeDocumento {

    private MapeadorDeDocumento() {
    }

    /**
     * O item gravado, de volta ao domínio.
     *
     * <p>Acrescentado na Etapa 11. A Etapa 5 só precisava do sentido de ida — o
     * papel de trabalho e a API liam apontamento, não item. A tela de conferência
     * de produto lê o item campo a campo, e a volta é onde a distinção da D002 se
     * perderia se alguém a escrevesse com pressa: <strong>coluna nula vira
     * {@code Optional.empty()}, jamais zero</strong>.</p>
     *
     * <p>A escala de cada valor monetário volta como foi gravada, porque as
     * colunas são {@code numeric} sem precisão declarada. É o outro lado da
     * escolha registrada na D006: "0" e "0,00" continuam sendo registros
     * diferentes do mesmo número depois de uma ida e volta ao banco.</p>
     */
    static ItemDocumento paraDominio(ItemDocumentoEntidade entidade) {
        if (entidade == null) {
            throw new PersistenciaInconsistente("Não há item gravado a converter.");
        }
        return new ItemDocumento(
                entidade.numeroItem(),
                entidade.ncm() == null ? Optional.empty() : Optional.of(new Ncm(entidade.ncm())),
                entidade.cfop() == null ? Optional.empty() : Optional.of(new Cfop(entidade.cfop())),
                entidade.valorItem(),
                cstGravado(entidade.cstIbs()),
                cstGravado(entidade.cstCbs()),
                entidade.codigoClassificacaoTributaria() == null
                        ? Optional.empty()
                        : Optional.of(new CodigoClassificacaoTributaria(
                                entidade.codigoClassificacaoTributaria())),
                Optional.ofNullable(entidade.baseCalculoIbs()),
                Optional.ofNullable(entidade.baseCalculoCbs()),
                Optional.ofNullable(entidade.aliquotaIbsUf()),
                Optional.ofNullable(entidade.aliquotaIbsMunicipal()),
                Optional.ofNullable(entidade.aliquotaCbs()),
                Optional.ofNullable(entidade.valorIbsUf()),
                Optional.ofNullable(entidade.valorIbsMunicipal()),
                Optional.ofNullable(entidade.valorCbs()));
    }

    private static Optional<CodigoCst> cstGravado(String gravado) {
        return gravado == null ? Optional.empty() : Optional.of(new CodigoCst(gravado));
    }
    /** Preenche a entidade com o estado atual do documento. */
    static void preencher(DocumentoEntidade entidade, Documento documento, Instant registradoEm) {
        entidade.atualizar(
                documento.modelo(),
                documento.serie(),
                documento.numero(),
                documento.dataEmissao(),
                documento.ufEmitente(),
                documento.ufDestinatario().orElse(null),
                documento.crtEmitente().orElse(null),
                documento.indicadorDestinatario().orElse(null),
                documento.identificadorEmitentePseudonimizado().valor(),
                documento.identificadorDestinatarioPseudonimizado()
                        .map(identificador -> identificador.valor())
                        .orElse(null),
                registradoEm);
    }

    /** Preenche a entidade do item com o estado atual do item e o resumo dele. */
    static void preencher(ItemDocumentoEntidade entidade, ItemDocumento item, String hashDoItem) {
        entidade.atualizar(
                hashDoItem,
                item.ncm().map(ncm -> ncm.valor()).orElse(null),
                item.cfop().map(cfop -> cfop.valor()).orElse(null),
                item.valorItem(),
                item.cstIbs().map(cst -> cst.valor()).orElse(null),
                item.cstCbs().map(cst -> cst.valor()).orElse(null),
                item.codigoClassificacaoTributaria().map(codigo -> codigo.valor()).orElse(null),
                quantia(item.baseCalculoIbs()),
                quantia(item.baseCalculoCbs()),
                quantia(item.aliquotaIbsUf()),
                quantia(item.aliquotaIbsMunicipal()),
                quantia(item.aliquotaCbs()),
                quantia(item.valorIbsUf()),
                quantia(item.valorIbsMunicipal()),
                quantia(item.valorCbs()));
    }

    private static BigDecimal quantia(Optional<BigDecimal> valor) {
        return valor.orElse(null);
    }
}
