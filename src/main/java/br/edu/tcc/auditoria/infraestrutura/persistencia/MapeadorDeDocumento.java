package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;

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
