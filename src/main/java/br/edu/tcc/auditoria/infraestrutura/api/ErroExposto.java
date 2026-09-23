package br.edu.tcc.auditoria.infraestrutura.api;

// Representa o corpo da resposta de erro: um código fixo para quem programa e a mensagem em português que as camadas de dentro já escreveram, repassada inteira.
public record ErroExposto(String erro, String mensagem) {
}
