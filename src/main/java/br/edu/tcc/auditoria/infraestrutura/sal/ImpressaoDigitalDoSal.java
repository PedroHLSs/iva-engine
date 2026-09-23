package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Representa a impressão digital do sal: SHA-256 de um rótulo fixo mais o sal, gravada no banco para perceber a troca de sal sem guardar o sal. Com sal de pelo menos 32 caracteres, ou gerado com 256 bits, não dá para descobrir o sal a partir dela.
public record ImpressaoDigitalDoSal(String valor) {

    private static final String ALGORITMO = "SHA-256";

    // Rótulo fixo que faz este resumo servir só como impressão digital de sal.
    private static final String ROTULO = "auditoria.sal.impressao-digital.v1";

    private static final int COMPRIMENTO_ESPERADO = 64;

    // Valida que a impressão digital tenha 64 caracteres, só com hexadecimal minúsculo.
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

    // Método estático que calcula a impressão digital do sal informado.
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

    // Retorna os 12 primeiros caracteres, para caber numa linha de log; eles não identificam o sal.
    public String abreviada() {
        return valor.substring(0, 12);
    }

    // Mostra só a forma curta da impressão digital.
    @Override
    public String toString() {
        return "ImpressaoDigitalDoSal[" + abreviada() + "...]";
    }

    // Método auxiliar que confere se o caractere é hexadecimal minúsculo.
    private static boolean ehHexadecimalMinusculo(int caractere) {
        return (caractere >= '0' && caractere <= '9') || (caractere >= 'a' && caractere <= 'f');
    }

    // Método auxiliar que escreve os bytes em hexadecimal minúsculo.
    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte parte : bytes) {
            texto.append(Character.forDigit((parte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(parte & 0xf, 16));
        }
        return texto.toString();
    }

    // Método auxiliar que cria um calculador de SHA-256.
    private static MessageDigest resumoNovo() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.", erro);
        }
    }
}
