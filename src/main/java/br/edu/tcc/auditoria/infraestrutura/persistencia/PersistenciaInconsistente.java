package br.edu.tcc.auditoria.infraestrutura.persistencia;

/**
 * Linha gravada que não corresponde a nenhum estado que o sistema saiba produzir.
 *
 * <p>Não é erro de uso: as restrições do banco impedem essas combinações. Quando
 * uma aparece, ou a linha foi escrita por outra versão do sistema, ou alterada
 * por fora. A leitura para em vez de adivinhar, porque adivinhar aqui produziria
 * um apontamento com conteúdo inventado.</p>
 */
class PersistenciaInconsistente extends RuntimeException {

    PersistenciaInconsistente(String mensagem) {
        super(mensagem);
    }
}
