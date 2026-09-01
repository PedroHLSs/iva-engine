package br.edu.tcc.auditoria.infraestrutura.acuracia;

import br.edu.tcc.auditoria.dominio.acuracia.Metrica;

import java.math.BigDecimal;

/**
 * Como uma {@link Metrica} vira texto, no arquivo e no terminal.
 *
 * <p>Fica num lugar só, e é usada pelos dois destinos, para que não exista a
 * possibilidade de o CSV e o terminal discordarem sobre o que escrever quando
 * não há número.</p>
 *
 * <h2>Métrica indefinida é escrita, nunca deixada em branco</h2>
 *
 * <p>É a D007 aplicada aqui: num arquivo lido meses depois, campo vazio é
 * indistinguível de campo que ninguém preencheu, e quem tiver pressa leria zero.
 * {@code (indefinida)} não se confunde com número nenhum e não entra por engano
 * em soma de ferramenta alguma.</p>
 *
 * <h2>Separador decimal é o ponto, nos dois destinos</h2>
 *
 * <p>Ponto, e não vírgula, porque o resultado desta etapa é feito para entrar
 * numa tabela e ser recontado. O mesmo texto no CSV e no terminal permite
 * conferir um contra o outro linha a linha, que é o que se faz quando o número
 * surpreende.</p>
 */
public final class TextoDeMetrica {

    /** O que se escreve no lugar de uma métrica sem denominador. */
    public static final String INDEFINIDA = "(indefinida)";

    private TextoDeMetrica() {
    }

    /** O valor com quatro casas decimais, ou {@link #INDEFINIDA}. */
    public static String de(Metrica metrica) {
        if (metrica == null) {
            throw new IllegalArgumentException("Não há métrica a escrever.");
        }
        return metrica.valor().map(BigDecimal::toPlainString).orElse(INDEFINIDA);
    }
}
