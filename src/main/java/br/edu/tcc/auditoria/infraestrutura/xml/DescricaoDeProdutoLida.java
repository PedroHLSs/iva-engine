package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A descrição de um item, endereçada pela identidade que o sistema já usa.
 *
 * <h2>O endereço é o resumo do item, e não uma chave nova</h2>
 *
 * <p>{@link HashDoItem} já identifica o item em toda parte: é a chave da
 * tratativa, é o que {@code item_da_execucao} grava, e é o endereço do produto na
 * interface. Usá-lo aqui faz a associação entre descrição e item ser a mesma
 * função com as mesmas entradas dos dois lados — quem grava e quem lê calculam o
 * endereço do mesmo jeito. Uma chave própria poderia divergir, e divergência aqui
 * não é linha faltando: é descrição trocada de produto.</p>
 *
 * <h2>Corrida de 44 dígitos não entra</h2>
 *
 * <p>xProd é texto digitado pelo emitente, e chave de acesso aparece nele com
 * frequência ("REF NF 3512..."). Os dígitos do meio da chave são o CNPJ do
 * emitente (D005), então a corrida é substituída pelo marcador antes de a
 * descrição sair daqui. É a mesma barreira de {@code OrigemDeArquivoIlegivel},
 * e a restrição da V8 é a segunda.</p>
 *
 * <p>O que nenhuma regra de forma alcança é prosa: "P/ OBRA FULANO" passa por
 * esta checagem e por qualquer outra automática. Está declarado como limitação,
 * e não disfarçado.</p>
 */
public record DescricaoDeProdutoLida(HashDoItem hashDoItem, Optional<String> descricao) {

    /** O que ocupa o lugar de uma chave de acesso encontrada na descrição. */
    public static final String MARCA_DA_CHAVE = "[chave-omitida]";

    private static final Pattern CORRIDA_DE_44_DIGITOS = Pattern.compile("[0-9]{44}");

    public DescricaoDeProdutoLida {
        if (hashDoItem == null) {
            throw new IllegalArgumentException(
                    "A descrição precisa do resumo do item a que pertence: é por ele que ela se liga "
                            + "ao produto certo.");
        }
        if (descricao == null) {
            throw new IllegalArgumentException(
                    "Descrição não declarada se representa com Optional.empty(), nunca com nulo.");
        }
    }

    /**
     * A descrição como veio no XML, saneada.
     *
     * <p>Texto nulo ou em branco vira {@code Optional.empty()}: o documento não
     * declarou nada, e branco é a mesma ausência escrita de outro jeito.</p>
     */
    public static DescricaoDeProdutoLida de(HashDoItem hashDoItem, String declarada) {
        if (declarada == null || declarada.isBlank()) {
            return new DescricaoDeProdutoLida(hashDoItem, Optional.empty());
        }
        String saneada = CORRIDA_DE_44_DIGITOS.matcher(declarada.strip())
                .replaceAll(java.util.regex.Matcher.quoteReplacement(MARCA_DA_CHAVE));
        return new DescricaoDeProdutoLida(
                hashDoItem, saneada.isBlank() ? Optional.empty() : Optional.of(saneada));
    }
}
