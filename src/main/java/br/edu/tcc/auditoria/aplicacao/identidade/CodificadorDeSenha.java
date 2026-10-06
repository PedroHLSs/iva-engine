package br.edu.tcc.auditoria.aplicacao.identidade;

// Interface responsável por gerar o hash de uma senha e conferir uma senha contra um hash. A aplicação não sabe qual algoritmo é usado; a infraestrutura usa BCrypt, que põe um sal diferente em cada senha.
public interface CodificadorDeSenha {

    // Gera o hash da senha, com sal novo.
    HashDeSenha codificar(SenhaInformada senha);

    // Diz se a senha corresponde ao hash gravado.
    boolean confere(SenhaInformada senha, HashDeSenha hash);
}
