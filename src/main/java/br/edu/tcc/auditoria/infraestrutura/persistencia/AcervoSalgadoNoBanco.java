package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Classe que conta e apaga o acervo que depende do sal. Apaga primeiro os documentos, que levam junto por cascata itens e apontamentos, e depois as execuções; nada aqui toca as tratativas.
@Component
public class AcervoSalgadoNoBanco implements AcervoSalgado {

    private final EntityManager entityManager;
    private final DocumentoJpa documentos;
    private final TratativaJpa tratativas;

    // Construtor que recebe o EntityManager e os repositórios de documentos e tratativas.
    AcervoSalgadoNoBanco(
            EntityManager entityManager, DocumentoJpa documentos, TratativaJpa tratativas) {
        this.entityManager = entityManager;
        this.documentos = documentos;
        this.tratativas = tratativas;
    }

    // Retorna quantos documentos estão gravados.
    @Override
    @Transactional(readOnly = true)
    public long quantidadeDeDocumentos() {
        return documentos.count();
    }

    // Apaga documentos e execuções direto em SQL, para valer a cascata do banco, e conta as tratativas preservadas.
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
