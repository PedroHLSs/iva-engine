package br.edu.tcc.auditoria.dominio.tratativa;

import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.excecao.TratativaInvalida;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.StringJoiner;

// Representa o resumo (SHA-256) do conteúdo do item, usado para reconhecer o mesmo item quando o lote é reprocessado. Não tem sal de propósito: trocar o sal não muda o resumo, e a tratativa continua valendo. Não acrescentar sal aqui.
public record HashDoItem(String valor) {

    private static final int COMPRIMENTO_ESPERADO = 64;
    private static final String ALGORITMO = "SHA-256";
    private static final String SEPARADOR_DE_CAMPO = "\u001f";
    private static final String MARCA_DE_AUSENCIA = "\u0000ausente";

    // Valida que o resumo tenha 64 caracteres, só com hexadecimal minúsculo.
    public HashDoItem {
        if (valor == null) {
            throw new TratativaInvalida("O resumo do item não pode ser nulo.");
        }
        if (valor.length() != COMPRIMENTO_ESPERADO) {
            throw new TratativaInvalida(
                    "O resumo do item deve ter %d caracteres hexadecimais, mas veio com %d."
                            .formatted(COMPRIMENTO_ESPERADO, valor.length()));
        }
        if (!ehHexadecimalMinusculo(valor)) {
            throw new TratativaInvalida(
                    "O resumo do item deve conter somente hexadecimal minúsculo (0-9, a-f).");
        }
    }

    // Método estático que calcula o resumo a partir da chave de acesso da nota e de todos os campos do item.
    public static HashDoItem de(ChaveAcesso chaveAcesso, ItemDocumento item) {
        if (chaveAcesso == null) {
            throw new TratativaInvalida(
                    "O resumo do item precisa da chave de acesso: o mesmo número de item existe em "
                            + "todo documento.");
        }
        if (item == null) {
            throw new TratativaInvalida("Não há item a resumir.");
        }
        return new HashDoItem(resumir(descrever(chaveAcesso, item)));
    }

    // Método auxiliar que escreve o item campo a campo num formato próprio; não usa toString() porque ele muda se um campo do record for renomeado.
    private static String descrever(ChaveAcesso chaveAcesso, ItemDocumento item) {
        StringJoiner descricao = new StringJoiner(SEPARADOR_DE_CAMPO);
        descricao.add(chaveAcesso.valor());
        descricao.add(Integer.toString(item.numeroItem()));
        descricao.add(texto(item.ncm().map(ncm -> ncm.valor())));
        descricao.add(texto(item.cfop().map(cfop -> cfop.valor())));
        descricao.add(quantia(item.valorItem()));
        descricao.add(texto(item.cstIbs().map(cst -> cst.valor())));
        descricao.add(texto(item.cstCbs().map(cst -> cst.valor())));
        descricao.add(texto(item.codigoClassificacaoTributaria().map(codigo -> codigo.valor())));
        descricao.add(texto(item.baseCalculoIbs().map(HashDoItem::quantia)));
        descricao.add(texto(item.baseCalculoCbs().map(HashDoItem::quantia)));
        descricao.add(texto(item.aliquotaIbsUf().map(HashDoItem::quantia)));
        descricao.add(texto(item.aliquotaIbsMunicipal().map(HashDoItem::quantia)));
        descricao.add(texto(item.aliquotaCbs().map(HashDoItem::quantia)));
        descricao.add(texto(item.valorIbsUf().map(HashDoItem::quantia)));
        descricao.add(texto(item.valorIbsMunicipal().map(HashDoItem::quantia)));
        descricao.add(texto(item.valorCbs().map(HashDoItem::quantia)));
        return descricao.toString();
    }

    // Método auxiliar que troca campo ausente por uma marca própria, para ausente não se confundir com zero.
    private static String texto(Optional<String> valor) {
        return valor.orElse(MARCA_DE_AUSENCIA);
    }

    // Método auxiliar que escreve o número mantendo as casas decimais: para a auditoria, 0 e 0,00 são registros diferentes.
    private static String quantia(BigDecimal valor) {
        return valor.toPlainString();
    }

    // Método auxiliar que calcula o SHA-256 do texto do item.
    private static String resumir(String descricao) {
        MessageDigest resumo;
        try {
            resumo = MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException semAlgoritmo) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.",
                    semAlgoritmo);
        }
        return emHexadecimalMinusculo(resumo.digest(descricao.getBytes(StandardCharsets.UTF_8)));
    }

    // Método auxiliar que converte o resultado do SHA-256 em texto hexadecimal minúsculo.
    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }

    // Método auxiliar que confere se o texto só tem dígitos de 0 a 9 e letras de a a f.
    private static boolean ehHexadecimalMinusculo(String texto) {
        return texto.chars().allMatch(caractere ->
                (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f'));
    }
}
