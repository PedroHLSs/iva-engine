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

// Classe que converte o documento e os itens entre o domínio e as linhas gravadas, escrita à mão para Optional vazio virar null, e nunca zero nem texto vazio, nos dois sentidos.
final class MapeadorDeDocumento {

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private MapeadorDeDocumento() {
    }

    // Método estático que remonta o item do domínio a partir da linha gravada: coluna null vira Optional vazio, e os valores voltam com as casas decimais gravadas. Acrescentado na Etapa 11, para a tela do produto.
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

    // Método auxiliar que converte o CST gravado, ou vazio quando a coluna é null.
    private static Optional<CodigoCst> cstGravado(String gravado) {
        return gravado == null ? Optional.empty() : Optional.of(new CodigoCst(gravado));
    }
    // Método estático que preenche a linha do documento com o estado atual do documento.
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

    // Método estático que preenche a linha do item com o estado atual do item e o hash dele.
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

    // Método auxiliar que troca Optional vazio por null.
    private static BigDecimal quantia(Optional<BigDecimal> valor) {
        return valor.orElse(null);
    }
}
