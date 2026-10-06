package br.edu.tcc.auditoria.infraestrutura.catalogo;

import java.util.Optional;

// Representa um problema encontrado na importação: o arquivo, e, quando o problema é de uma linha, o número dela, a coluna e o valor como veio. Problema do arquivo inteiro, como coluna faltando no cabeçalho, vem sem linha.
public record LinhaRecusada(
        String arquivo,
        Optional<Integer> linha,
        Optional<String> coluna,
        Optional<String> valor,
        String motivo) {

    // Valida que o problema diga o arquivo e o motivo, e que os opcionais não venham nulos.
    public LinhaRecusada {
        if (arquivo == null || motivo == null || linha == null || coluna == null || valor == null) {
            throw new ImportacaoDeCatalogoInvalida(
                    "A linha recusada precisa do arquivo e do motivo; o resto é Optional, nunca nulo.");
        }
    }

    // Escreve o problema numa linha de texto: arquivo, linha, coluna, valor e motivo.
    public String comoTexto() {
        StringBuilder texto = new StringBuilder(arquivo).append(": ");
        linha.ifPresent(numero -> texto.append("Linha ").append(numero));
        coluna.ifPresent(nome -> texto.append(linha.isPresent() ? ", " : "")
                .append("coluna \"").append(nome).append('"'));
        valor.ifPresent(conteudo -> texto.append(", valor ")
                .append(conteudo.isBlank() ? "em branco" : "\"" + conteudo + "\""));
        if (linha.isPresent() || coluna.isPresent()) {
            texto.append(" — ");
        }
        return texto.append(motivo).toString();
    }
}
