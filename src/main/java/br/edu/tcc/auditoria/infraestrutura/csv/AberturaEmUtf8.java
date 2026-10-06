package br.edu.tcc.auditoria.infraestrutura.csv;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Classe que é o único ponto de abertura dos arquivos de texto que entram no sistema — os CSV do catálogo, venham de uma pasta ou de um envio pela web, e o gabarito (D024). Decodifica o arquivo inteiro em UTF-8 estrito antes de entregá-lo: byte que não forma caractere em UTF-8 recusa o arquivo, com o nome, a linha e a codificação esperada. Nunca substitui caractere: um "?" gravado no lugar de um acento é um dado que o arquivo não tinha.
public final class AberturaEmUtf8 {

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private AberturaEmUtf8() {
    }

    // Método estático que lê o arquivo do disco e o abre pelo mesmo caminho do conteúdo em memória.
    public static Reader abrir(Path arquivo, RecusaDeCsv recusa) throws IOException {
        return abrir(Files.readAllBytes(arquivo), arquivo.getFileName().toString(), recusa);
    }

    // Método estático que decodifica o conteúdo em UTF-8 estrito; fora de UTF-8, recusa pelo RecusaDeCsv de quem chama.
    public static Reader abrir(byte[] conteudo, String nomeDoArquivo, RecusaDeCsv recusa) {
        CharsetDecoder decodificador = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer bytes = ByteBuffer.wrap(conteudo);
        try {
            return new StringReader(decodificador.decode(bytes).toString());
        } catch (CharacterCodingException foraDeUtf8) {
            int posicao = bytes.position();
            throw recusa.de(("O arquivo \"%s\" não está em UTF-8, que é a codificação esperada: a linha %d tem o "
                    + "byte 0x%02X, que não forma caractere em UTF-8. Salve o arquivo em UTF-8 e envie de novo; "
                    + "o sistema não troca por \"?\" o caractere que não consegue ler.")
                    .formatted(nomeDoArquivo, linhaDe(conteudo, posicao), conteudo[posicao] & 0xFF), foraDeUtf8);
        }
    }

    // Método auxiliar que conta em que linha está o byte, contando as quebras antes dele.
    private static int linhaDe(byte[] conteudo, int posicao) {
        int linha = 1;
        for (int i = 0; i < posicao; i++) {
            if (conteudo[i] == '\n') {
                linha++;
            }
        }
        return linha;
    }
}
