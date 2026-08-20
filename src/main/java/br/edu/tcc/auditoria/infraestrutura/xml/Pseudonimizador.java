package br.edu.tcc.auditoria.infraestrutura.xml;

import br.edu.tcc.auditoria.dominio.IdentificadorPseudonimizado;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Transforma o CNPJ ou o CPF lido do XML no pseudônimo que o domínio aceita.
 *
 * <p>Esta é a única classe do sistema que recebe identificador de participante
 * em texto claro. Ela devolve {@link IdentificadorPseudonimizado}, que só aceita
 * resumo criptográfico, e a partir daí o valor original não tem mais como
 * circular: não existe campo para ele no modelo de domínio.</p>
 *
 * <h2>Como o pseudônimo é calculado</h2>
 *
 * <p>SHA-256 sobre o sal de instalação, um separador, e o identificador. O
 * separador impede que dois pares distintos de (sal, identificador) produzam a
 * mesma sequência de bytes — sem ele, o sal {@code "AB"} com o identificador
 * {@code "12"} e o sal {@code "A"} com o identificador {@code "B12"} teriam o
 * mesmo resumo.</p>
 *
 * <p>Não é derivação lenta de senha. Contra um adversário que conheça o sal, o
 * espaço de CNPJ é pequeno o bastante para ser percorrido inteiro, e trocar o
 * algoritmo não muda isso — o que protege o pseudônimo é o sal ser secreto, e é
 * por isso que {@link SalDeInstalacao} se recusa a ter valor padrão.</p>
 *
 * <h2>O que não é pseudonimizado</h2>
 *
 * <p>A chave de acesso do documento é preservada como veio, porque é a
 * identidade do documento auditado e precisa aparecer no relatório. Ela carrega
 * o CNPJ do emitente nas suas posições intermediárias, de modo que documento com
 * participantes pseudonimizados não é documento anônimo. Ver D005 em
 * {@code docs/DECISOES-ARQUITETURA.md}.</p>
 */
public final class Pseudonimizador {

    private static final String ALGORITMO = "SHA-256";

    /** Separador entre sal e identificador. Não imprimível, não ocorre em CNPJ nem CPF. */
    private static final byte SEPARADOR = 0x1f;

    private final SalDeInstalacao sal;

    public Pseudonimizador(SalDeInstalacao sal) {
        if (sal == null) {
            throw new SalDeInstalacaoInvalido(
                    "O pseudonimizador exige um sal de instalação. Ver SalDeInstalacao.daConfiguracaoExterna().");
        }
        this.sal = sal;
    }

    /**
     * Devolve o pseudônimo do identificador informado.
     *
     * <p>O identificador é usado como veio no documento, apenas sem os espaços
     * em volta. Não há normalização de máscara: o leiaute da NF-e traz CNPJ e CPF
     * só com dígitos, e "consertar" o que fugir disso faria dois valores
     * diferentes virarem o mesmo pseudônimo sem que ninguém visse.</p>
     *
     * @throws DocumentoFiscalIlegivel se o identificador for nulo ou vazio
     */
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

    private static MessageDigest resumoNovo() {
        try {
            // Uma instância por chamada: MessageDigest guarda estado e não é reutilizável em paralelo.
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(
                    "A plataforma não oferece " + ALGORITMO + ", que é exigido por toda JVM.", erro);
        }
    }

    private static String emHexadecimalMinusculo(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte umByte : bytes) {
            texto.append(Character.forDigit((umByte >> 4) & 0xf, 16));
            texto.append(Character.forDigit(umByte & 0xf, 16));
        }
        return texto.toString();
    }
}
