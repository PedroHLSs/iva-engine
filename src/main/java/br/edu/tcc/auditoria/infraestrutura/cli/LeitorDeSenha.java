package br.edu.tcc.auditoria.infraestrutura.cli;

import java.util.Optional;

// Interface que pede a senha a quem está no terminal, sem mostrar o que é digitado. A senha nunca entra como opção da linha de comando, porque opção fica no histórico do terminal e na lista de processos da máquina.
interface LeitorDeSenha {

    // Pede a senha com o texto informado; vazio quando não há terminal interativo.
    Optional<String> pedir(String pergunta);
}
