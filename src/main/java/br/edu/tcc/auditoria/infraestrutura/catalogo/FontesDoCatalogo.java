package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.infraestrutura.csv.AberturaEmUtf8;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

// Interface que entrega os arquivos CSV do catálogo pelo nome, venham de uma pasta (linha de comando) ou de um envio pela web. Acrescentada na Etapa 12, para os dois caminhos passarem pela mesma leitura e pelas mesmas recusas.
// Emenda de 04/10/2026 (D024): os dois caminhos abrem o arquivo por AberturaEmUtf8, em UTF-8 estrito. Até essa data cada um decodificava do seu jeito: a pasta recusava o arquivo fora de UTF-8 com MalformedInputException, sem dizer a codificação esperada, e o envio o aceitava, trocando cada acento por "?" — o mesmo arquivo, gravado pela web e recusado pela CLI. Arquivo fora de UTF-8 é recusado com ImportacaoDeCatalogoInvalida, nos dois.
public interface FontesDoCatalogo {

    // Abre o arquivo pelo nome, ou devolve vazio se ele não veio; recusa com ImportacaoDeCatalogoInvalida o arquivo fora de UTF-8.
    Optional<Reader> abrir(String nomeDoArquivo) throws IOException;

    // Diz de onde vêm os arquivos, para a mensagem de erro.
    String descricao();

    // Método estático que lê os arquivos de uma pasta, em UTF-8 estrito.
    static FontesDoCatalogo daPasta(Path diretorio) {
        return new FontesDoCatalogo() {
            @Override
            public Optional<Reader> abrir(String nomeDoArquivo) throws IOException {
                Path arquivo = diretorio.resolve(nomeDoArquivo);
                if (!Files.isRegularFile(arquivo)) {
                    return Optional.empty();
                }
                return Optional.of(AberturaEmUtf8.abrir(
                        Files.readAllBytes(arquivo), nomeDoArquivo, RecusaDoCatalogoEmCsv.INSTANCIA));
            }

            @Override
            public String descricao() {
                return "\"" + diretorio + "\"";
            }
        };
    }

    // Método estático que lê arquivos recebidos em memória, pelo nome, em UTF-8 estrito.
    static FontesDoCatalogo emMemoria(Map<String, byte[]> conteudoPorNome) {
        Map<String, byte[]> copia = Map.copyOf(conteudoPorNome);
        return new FontesDoCatalogo() {
            @Override
            public Optional<Reader> abrir(String nomeDoArquivo) {
                byte[] conteudo = copia.get(nomeDoArquivo);
                if (conteudo == null) {
                    return Optional.empty();
                }
                return Optional.of(AberturaEmUtf8.abrir(conteudo, nomeDoArquivo, RecusaDoCatalogoEmCsv.INSTANCIA));
            }

            @Override
            public String descricao() {
                return "o envio";
            }
        };
    }
}
