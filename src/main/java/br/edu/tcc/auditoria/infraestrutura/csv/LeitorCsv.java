package br.edu.tcc.auditoria.infraestrutura.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Classe que lê os CSV que entram no sistema: separador ;, UTF-8, linha com # é comentário, linha em branco é ignorada, e a primeira linha que sobra é o cabeçalho. Campo entre aspas pode ter ;, e "" vale uma aspa. Quem chama diz, por RecusaDeCsv, como nomear o erro.
public final class LeitorCsv {

    private static final char SEPARADOR = ';';
    private static final char ASPAS = '"';
    private static final String MARCA_DE_COMENTARIO = "#";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private LeitorCsv() {
    }

    // Método estático que lê o CSV inteiro e devolve as linhas de dados; recusa arquivo sem cabeçalho.
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

    // Método auxiliar que diz se a linha está em branco ou é comentário.
    private static boolean ehIgnoravel(String conteudo) {
        String semEspacos = conteudo.strip();
        return semEspacos.isEmpty() || semEspacos.startsWith(MARCA_DE_COMENTARIO);
    }

    // Método auxiliar que confere o cabeçalho; recusa coluna sem nome ou repetida.
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

    // Método auxiliar que monta a linha de dados; recusa se o número de campos não bater com o cabeçalho.
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

    // Método auxiliar que separa os campos da linha pelo ;, respeitando as aspas.
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
