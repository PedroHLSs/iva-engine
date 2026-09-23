package br.edu.tcc.auditoria.dominio.excecao;

// Classe abstrata que é a raiz de toda exceção do domínio; o domínio não usa exceção genérica da biblioteca padrão, e cada tipo de dado inválido tem subclasse própria.
public abstract class ExcecaoDeDominio extends RuntimeException {

    // Construtor que recebe a mensagem de erro e chama RuntimeException com essa mensagem.
    protected ExcecaoDeDominio(String mensagem) {
        super(mensagem);
    }
}
