package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.Metrica;

import java.math.BigDecimal;

// Classe que transforma uma métrica em texto, igual no CSV e no terminal: número com ponto decimal, ou (indefinida) quando não dá para calcular. Nunca deixa em branco, porque campo vazio seria lido como zero.
public final class TextoDeMetrica {

    // Texto escrito no lugar de uma métrica que não dá para calcular.
    public static final String INDEFINIDA = "(indefinida)";

    // Construtor privado: ninguém cria objeto desta classe, só usa o método estático.
    private TextoDeMetrica() {
    }

    // Método estático que devolve o valor com quatro casas decimais, ou (indefinida).
    public static String de(Metrica metrica) {
        if (metrica == null) {
            throw new IllegalArgumentException("Não há métrica a escrever.");
        }
        return metrica.valor().map(BigDecimal::toPlainString).orElse(INDEFINIDA);
    }
}
