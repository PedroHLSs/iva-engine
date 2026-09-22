package br.edu.tcc.auditoria.infraestrutura.api;

/**
 * Corpo de resposta de erro.
 *
 * <p>{@code erro} é um código estável para quem programa; {@code mensagem} é o
 * texto em português que as camadas de dentro já escreveram, e que costuma ser
 * acionável — "linha 7 sem fonteNormativa" em vez de "erro de importação". Ela
 * é repassada inteira em vez de trocada por um texto genérico da fronteira.</p>
 */
public record ErroExposto(String erro, String mensagem) {
}
