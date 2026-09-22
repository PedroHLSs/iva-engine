package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * O acervo que depende do sal, apagável por comando explícito.
 *
 * <h2>A ordem do apagamento não é arbitrária</h2>
 *
 * <p>Apagar {@code documento} primeiro leva junto, por cascata do próprio banco,
 * {@code item_documento}, {@code achado}, {@code achado_evidencia} e
 * {@code achado_da_execucao}. Só depois disso as linhas de
 * {@code execucao_auditoria} ficam sem quem as referencie e podem sair — o
 * apontamento aponta para a execução que o gerou, e essa chave estrangeira não
 * tem cascata de propósito.</p>
 *
 * <p>O apagamento é feito em SQL, e não linha a linha pelo JPA, para que a
 * cascata seja a do banco. Carregar dezenas de milhares de documentos em memória
 * só para apagá-los seria lento e daria o mesmo resultado.</p>
 *
 * <h2>Tratativa fica</h2>
 *
 * <p>Nenhuma instrução aqui toca {@code tratativa}, e a tabela não tem chave
 * estrangeira para {@code documento} — a ausência de cascata é estrutural, não
 * um descuido a corrigir. Ver {@link AcervoSalgado}.</p>
 */
@Component
public class AcervoSalgadoNoBanco implements AcervoSalgado {

    private final EntityManager entityManager;
    private final DocumentoJpa documentos;
    private final TratativaJpa tratativas;

    AcervoSalgadoNoBanco(
            EntityManager entityManager, DocumentoJpa documentos, TratativaJpa tratativas) {
        this.entityManager = entityManager;
        this.documentos = documentos;
        this.tratativas = tratativas;
    }

    @Override
    @Transactional(readOnly = true)
    public long quantidadeDeDocumentos() {
        return documentos.count();
    }

    @Override
    @Transactional
    public Apagamento apagar() {
        long tratativasAntes = tratativas.count();

        int documentosApagados = entityManager.createNativeQuery("delete from documento")
                .executeUpdate();
        int execucoesApagadas = entityManager.createNativeQuery("delete from execucao_auditoria")
                .executeUpdate();

        return new Apagamento(documentosApagados, execucoesApagadas, tratativasAntes);
    }
}
