package br.edu.tcc.auditoria.aplicacao.identidade;

import java.nio.charset.StandardCharsets;

// Representa a senha como a pessoa digitou, só pelo tempo de conferir ou de gerar o hash. O toString nunca mostra o valor, para a senha não cair em log nem em mensagem de erro por descuido.
public record SenhaInformada(String valor) {

    // Tamanho mínimo aceito para senha nova, em caracteres.
    public static final int TAMANHO_MINIMO = 12;

    // O BCrypt só considera os primeiros 72 bytes; senha maior seria cortada em silêncio.
    public static final int BYTES_MAXIMOS = 72;

    // Valida que a senha tenha sido informada.
    public SenhaInformada {
        if (valor == null || valor.isEmpty()) {
            throw new IdentidadeInvalida("A senha não foi informada.");
        }
    }

    // Confere se a senha serve como senha nova: tamanho mínimo e limite do BCrypt.
    public void exigirQueSirvaComoSenhaNova() {
        if (valor.isBlank()) {
            throw new IdentidadeInvalida("A senha não pode ser só espaços.");
        }
        if (valor.codePointCount(0, valor.length()) < TAMANHO_MINIMO) {
            throw new IdentidadeInvalida(
                    "A senha precisa ter pelo menos %d caracteres.".formatted(TAMANHO_MINIMO));
        }
        if (valor.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            throw new IdentidadeInvalida(
                    ("A senha passa de %d bytes. O BCrypt ignora o que vem depois disso, e uma senha "
                            + "cortada em silêncio não é a senha que a pessoa escolheu.")
                            .formatted(BYTES_MAXIMOS));
        }
    }

    // Nunca mostra o valor.
    @Override
    public String toString() {
        return "SenhaInformada[omitida]";
    }
}
