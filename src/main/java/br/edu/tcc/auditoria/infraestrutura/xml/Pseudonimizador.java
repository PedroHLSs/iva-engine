package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Classe que transforma o CNPJ ou o CPF lido do XML no pseudônimo que o domínio aceita, com SHA-256 sobre o sal, um separador e o identificador. É a única classe que recebe o identificador em texto claro; a chave de acesso não passa por aqui e continua levando o CNPJ do emitente.
public final class Pseudonimizador {

    private static final String ALGORITMO = "SHA-256";

    // Separador entre o sal e o identificador; não aparece em CNPJ nem em CPF.
    private static final byte SEPARADOR = 0x1f;

    private final SalDeInstalacao sal;

    // Construtor que recebe o sal da instalação; recusa sal nulo.
    public Pseudonimizador(SalDeInstalacao sal) {
        if (sal == null) {
            throw new SalDeInstalacaoInvalido(
                    "O pseudonimizador exige um sal de instalação. Ver SalDeInstalacao.daConfiguracaoExterna().");
        }
        this.sal = sal;
    }

    // Devolve o pseudônimo do identificador, usado como veio, só sem os espaços em volta; recusa identificador vazio.
    public IdentificadorPseudonimizado pseudonimizar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new DocumentoFiscalIlegivel(
                    "Não há identificador de participante a pseudonimizar. Participante sem CNPJ, CPF nem "
                            + "identificador de estrangeiro é ausência de participante, e se representa com "
                            + "Optional.empty().");
        }

        MessageDigest resumo = resumoNovo();
        resumo.update(sal.bytes());
        resumo.update(SEPARADOR);
        resumo.update(identificador.strip().getBytes(StandardCharsets.UTF_8));

        return new IdentificadorPseudonimizado(emHexadecimalMinusculo(resumo.digest()));
    }

    // Método auxiliar que cria um calculador de SHA-256.
    private static MessageDigest resumoNovo() {
        try {
            // Uma instância por chamada: MessageDigest guarda estado e não é reutilizável em paralelo.
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.", erro);
        }
    }

    // Método auxiliar que escreve os bytes em hexadecimal minúsculo.
    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }
}
