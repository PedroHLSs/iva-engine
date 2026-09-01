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

/**
 * Resumo criptográfico do conteúdo declarado de um item, usado como identidade
 * estável do que foi auditado.
 *
 * <h2>Por que existe</h2>
 *
 * <p>Um apontamento precisa poder ser reencontrado depois de o lote ser
 * reprocessado, para que a tratativa dada por uma pessoa não se perca. A linha
 * do banco não serve como identidade: ela é apagada e recriada. O número do
 * item tampouco basta sozinho, porque o mesmo número existe em todo documento.
 * O que identifica o item é <em>o documento em que está</em> mais <em>o que ele
 * declara</em>, e é exatamente isso que este resumo cobre.</p>
 *
 * <h2>O que entra no resumo</h2>
 *
 * <p>A chave de acesso, o número do item e todos os campos declarados do item,
 * na ordem em que {@link ItemDocumento} os declara. Consequências deliberadas:</p>
 *
 * <ul>
 *   <li>Reprocessar o mesmo arquivo produz o mesmo resumo — a tratativa se
 *       aplica de novo.</li>
 *   <li>Se o conteúdo do item mudar, o resumo muda e o apontamento reabre. A
 *       tratativa foi dada sobre um item concreto; item diferente é pergunta
 *       nova.</li>
 *   <li>Campo ausente e campo com zero geram resumos diferentes, porque a
 *       marca de ausência é distinta de qualquer valor. Apagar essa diferença
 *       aqui faria duas situações fiscais distintas compartilharem tratativa.</li>
 *   <li>A escala declarada entra no resumo: {@code 0} e {@code 0,00} são
 *       registros diferentes do mesmo número para a auditoria, e continuam
 *       diferentes aqui.</li>
 * </ul>
 *
 * <p>Não é pseudonimização e não tem sal: o objetivo é identidade reproduzível
 * entre execuções e entre instalações, não sigilo. A chave de acesso já é
 * gravada em texto no repositório de documentos auditados — nada que este
 * resumo cobre é segredo. Dado pessoal do participante não entra aqui nem lá:
 * emitente e destinatário não fazem parte do item.</p>
 */
public record HashDoItem(String valor) {

    private static final int COMPRIMENTO_ESPERADO = 64;
    private static final String ALGORITMO = "SHA-256";
    private static final String SEPARADOR_DE_CAMPO = "\u001f";
    private static final String MARCA_DE_AUSENCIA = "\u0000ausente";

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

    /** Calcula o resumo do item tal como declarado no documento indicado. */
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

    /**
     * Descrição canônica do item, campo a campo.
     *
     * <p>Formato próprio, e não {@code toString()}: o resumo precisa ser estável
     * entre versões do código, e {@code toString()} de {@code record} muda com o
     * nome dos componentes.</p>
     */
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

    private static String texto(Optional<String> valor) {
        return valor.orElse(MARCA_DE_AUSENCIA);
    }

    /** Preserva a escala declarada: para a auditoria, "0" e "0,00" não são o mesmo registro. */
    private static String quantia(BigDecimal valor) {
        return valor.toPlainString();
    }

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

    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }

    private static boolean ehHexadecimalMinusculo(String texto) {
        return texto.chars().allMatch(caractere ->
                (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f'));
    }
}
