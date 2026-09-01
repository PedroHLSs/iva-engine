package br.edu.tcc.auditoria.infraestrutura.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor dos CSVs que entram no sistema.
 *
 * <p>Convenções do formato, todas escolhidas por conveniência de operação e
 * nenhuma delas com significado normativo:</p>
 *
 * <ul>
 *   <li>separador de campo {@code ;}, para não disputar com a vírgula decimal;</li>
 *   <li>codificação UTF-8, definida por quem abre o {@link Reader};</li>
 *   <li>linhas iniciadas por {@code #} são comentário e linhas em branco são
 *       ignoradas — é o que permite ao arquivo declarar no topo que seu
 *       conteúdo é fictício;</li>
 *   <li>a primeira linha que não for comentário nem branco é o cabeçalho;</li>
 *   <li>campo entre aspas duplas pode conter o separador, e {@code ""} representa
 *       uma aspa literal. Aspas não podem envolver quebra de linha: a numeração
 *       de linha precisa continuar valendo para as mensagens de erro.</li>
 * </ul>
 *
 * <p>Nasceu na Etapa 2, dentro de {@code infraestrutura.catalogo}, e mudou para
 * cá na Etapa 7, quando o gabarito de acurácia passou a precisar do mesmo
 * formato — ver D008. O leitor não sabe de que assunto é o arquivo: quem o chama
 * informa, por {@link RecusaDeCsv}, como uma recusa deve ser nomeada.</p>
 */
public final class LeitorCsv {

    private static final char SEPARADOR = ';';
    private static final char ASPAS = '"';
    private static final String MARCA_DE_COMENTARIO = "#";

    private LeitorCsv() {
    }

    /**
     * Lê o CSV inteiro.
     *
     * @param origem de onde ler; a codificação é de quem abriu o {@link Reader}
     * @param recusa como nomear a falha quando o arquivo estiver malformado
     */
    public static List<LinhaCsv> ler(Reader origem, RecusaDeCsv recusa) throws IOException {
        if (recusa == null) {
            throw new IllegalArgumentException(
                    "O leitor de CSV precisa saber como recusar um arquivo malformado.");
        }
        if (origem == null) {
            throw recusa.de("Nenhuma origem de CSV informada para leitura.");
        }

        List<String> cabecalho = null;
        List<LinhaCsv> linhas = new ArrayList<>();
        int numeroDaLinha = 0;

        try (BufferedReader leitor = new BufferedReader(origem)) {
            String conteudo;
            while ((conteudo = leitor.readLine()) != null) {
                numeroDaLinha++;
                if (ehIgnoravel(conteudo)) {
                    continue;
                }
                List<String> campos = dividir(conteudo);
                if (cabecalho == null) {
                    cabecalho = validarCabecalho(campos, numeroDaLinha, recusa);
                    continue;
                }
                linhas.add(montarLinha(cabecalho, campos, numeroDaLinha, recusa));
            }
        }

        if (cabecalho == null) {
            throw recusa.de(
                    "O arquivo não tem cabeçalho: só foram encontradas linhas em branco ou comentários.");
        }
        return List.copyOf(linhas);
    }

    private static boolean ehIgnoravel(String conteudo) {
        String semEspacos = conteudo.strip();
        return semEspacos.isEmpty() || semEspacos.startsWith(MARCA_DE_COMENTARIO);
    }

    private static List<String> validarCabecalho(
            List<String> campos, int numeroDaLinha, RecusaDeCsv recusa) {

        List<String> colunas = campos.stream().map(String::strip).toList();
        for (String coluna : colunas) {
            if (coluna.isBlank()) {
                throw recusa.de("Linha %d: o cabeçalho tem coluna sem nome.".formatted(numeroDaLinha));
            }
            if (colunas.indexOf(coluna) != colunas.lastIndexOf(coluna)) {
                throw recusa.de(
                        "Linha %d: o cabeçalho repete a coluna \"%s\".".formatted(numeroDaLinha, coluna));
            }
        }
        return colunas;
    }

    private static LinhaCsv montarLinha(
            List<String> cabecalho, List<String> campos, int numeroDaLinha, RecusaDeCsv recusa) {

        if (campos.size() != cabecalho.size()) {
            throw recusa.de(
                    "Linha %d: o cabeçalho tem %d colunas, mas a linha tem %d campos."
                            .formatted(numeroDaLinha, cabecalho.size(), campos.size()));
        }
        Map<String, String> valores = new LinkedHashMap<>();
        for (int posicao = 0; posicao < cabecalho.size(); posicao++) {
            valores.put(cabecalho.get(posicao), campos.get(posicao));
        }
        return new LinhaCsv(numeroDaLinha, valores, recusa);
    }

    private static List<String> dividir(String conteudo) {
        List<String> campos = new ArrayList<>();
        StringBuilder campoAtual = new StringBuilder();
        boolean entreAspas = false;

        for (int posicao = 0; posicao < conteudo.length(); posicao++) {
            char caractere = conteudo.charAt(posicao);
            if (entreAspas) {
                boolean aspaEscapada = caractere == ASPAS
                        && posicao + 1 < conteudo.length()
                        && conteudo.charAt(posicao + 1) == ASPAS;
                if (aspaEscapada) {
                    campoAtual.append(ASPAS);
                    posicao++;
                } else if (caractere == ASPAS) {
                    entreAspas = false;
                } else {
                    campoAtual.append(caractere);
                }
            } else if (caractere == ASPAS) {
                entreAspas = true;
            } else if (caractere == SEPARADOR) {
                campos.add(campoAtual.toString());
                campoAtual.setLength(0);
            } else {
                campoAtual.append(caractere);
            }
        }
        campos.add(campoAtual.toString());
        return campos;
    }
}
