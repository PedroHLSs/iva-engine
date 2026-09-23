package br.edu.tcc.auditoria.infraestrutura.persistencia;

class PersistenciaInconsistente extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    PersistenciaInconsistente(String mensagem) {
        super(mensagem);
    }
}
