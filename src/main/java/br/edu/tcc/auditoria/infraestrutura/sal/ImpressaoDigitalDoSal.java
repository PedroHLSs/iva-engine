package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Resumo criptográfico do sal em uso, gravado no banco para que a troca de sal
 * não passe despercebida.
 *
 * <h2>O sal nunca é gravado, só a impressão digital</h2>
 *
 * <p>O que vai para o banco é SHA-256 sobre um rótulo fixo mais o sal. Isso
 * basta para comparar dois sais sem guardar nenhum dos dois: se a impressão
 * digital bate, é o mesmo sal; se não bate, é outro.</p>
 *
 * <p>O rótulo fixo é separação de domínio. Sem ele, o valor gravado aqui seria
 * o mesmo resumo que qualquer outra ferramenta produziria sobre o mesmo sal, e
 * duas coisas que deveriam ser independentes passariam a se confirmar
 * mutuamente. Com ele, a impressão digital só serve para o que foi feita.</p>
 *
 * <p>Não é o resumo usado na pseudonimização, e nem poderia ser: aquele leva o
 * identificador do participante junto. Trocar um pelo outro faria a coluna do
 * banco carregar algo derivado de dado pessoal.</p>
 *
 * <h2>Por que isto não enfraquece o sal</h2>
 *
 * <p>Quem obtiver o banco obtém a impressão digital, e a partir dela poderia
 * tentar descobrir o sal por força bruta. Isso só funciona se o sal for
 * adivinhável — e o piso de {@code SalDeInstalacao} é de 32 caracteres, com o
 * sal gerado automaticamente vindo de 256 bits de {@code SecureRandom}. Contra
 * um segredo desse tamanho a força bruta não termina.</p>
 */
public record ImpressaoDigitalDoSal(String valor) {

    private static final String ALGORITMO = "SHA-256";

    /** Separação de domínio: este resumo só vale como impressão digital de sal. */
    private static final String ROTULO = "auditoria.sal.impressao-digital.v1";

    private static final int COMPRIMENTO_ESPERADO = 64;

    public ImpressaoDigitalDoSal {
        if (valor == null) {
            throw new SalTrocado("A impressão digital do sal não pode ser nula.");
        }
        if (valor.length() != COMPRIMENTO_ESPERADO) {
            throw new SalTrocado(
                    "A impressão digital do sal deve ter %d caracteres hexadecimais, mas veio com %d."
                            .formatted(COMPRIMENTO_ESPERADO, valor.length()));
        }
        if (!valor.chars().allMatch(ImpressaoDigitalDoSal::ehHexadecimalMinusculo)) {
            throw new SalTrocado(
                    "A impressão digital do sal deve conter somente hexadecimal minúsculo (0-9, a-f).");
        }
    }

    /** Calcula a impressão digital do sal informado. */
    public static ImpressaoDigitalDoSal de(SalDeInstalacao sal) {
        if (sal == null) {
            throw new SalTrocado("Não há sal a resumir.");
        }
        MessageDigest resumo = resumoNovo();
        resumo.update(ROTULO.getBytes(StandardCharsets.UTF_8));
        resumo.update((byte) 0x1f);
        resumo.update(sal.valor().getBytes(StandardCharsets.UTF_8));
        return new ImpressaoDigitalDoSal(emHexadecimalMinusculo(resumo.digest()));
    }

    /**
     * Forma curta, para caber numa linha de log sem virar ruído.
     *
     * <p>Doze caracteres não identificam o sal e bastam para uma pessoa comparar
     * duas linhas de diagnóstico a olho. O valor inteiro continua disponível.</p>
     */
    public String abreviada() {
        return valor.substring(0, 12);
    }

    @Override
    public String toString() {
        return "ImpressaoDigitalDoSal[" + abreviada() + "...]";
    }

    private static boolean ehHexadecimalMinusculo(int caractere) {
        return (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f');
    }

    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte parte : bytes) {
            texto.append(Character.forDigit((parte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(parte & 0xf, 16));
        }
        return texto.toString();
    }

    private static MessageDigest resumoNovo() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.", erro);
        }
    }
}
