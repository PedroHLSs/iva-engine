package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.Optional;
import java.util.regex.Pattern;

// Representa a descrição de um item (xProd), ligada ao item pelo HashDoItem, o mesmo endereço que o resto do sistema usa. Sequência de 44 dígitos é trocada por [chave-omitida], porque a chave de acesso contém o CNPJ; texto livre, como nome de cliente, não é pego.
public record DescricaoDeProdutoLida(HashDoItem hashDoItem, Optional<String> descricao) {

    // Marca que fica no lugar de uma chave de acesso achada na descrição.
    public static final String MARCA_DA_CHAVE = "[chave-omitida]";

    private static final Pattern CORRIDA_DE_44_DIGITOS = Pattern.compile("[0-9]{44}");

    // Valida que haja o hash do item e que a descrição seja Optional, nunca nula.
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

    // Método estático que cria a descrição a partir do texto do XML: nulo ou em branco vira vazio, e chave de acesso vira a marca.
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
