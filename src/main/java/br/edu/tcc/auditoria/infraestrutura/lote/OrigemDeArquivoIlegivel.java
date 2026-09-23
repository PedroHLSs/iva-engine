package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Classe que converte a falha de leitura no registro que a análise grava, tirando do nome do arquivo a pasta de quem rodou e o CNPJ do emitente que vem dentro da chave de acesso. O resto da chave fica, para a pessoa achar o arquivo; nome escrito à mão com razão social não é pego.
public final class OrigemDeArquivoIlegivel {

    // Separador que o LeitorLote usa entre o pacote e a entrada.
    private static final char SEPARADOR_DE_ENTRADA = '!';

    // Barra do Windows, escrita pelo código do caractere para não depender de escape.
    private static final char BARRA_INVERTIDA = (char) 92;

    // Qualquer sequência de 44 dígitos, que é a forma de uma chave de acesso.
    private static final Pattern FORMA_DE_CHAVE = Pattern.compile("[0-9]{44}");

    // Posições da chave, contando do zero, em que fica o CNPJ do emitente.
    private static final int INICIO_DO_CNPJ = 6;
    private static final int FIM_DO_CNPJ = 20;

    static final String MARCA_DO_CNPJ = "[cnpj-oculto]";

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private OrigemDeArquivoIlegivel() {
    }

    // Método estático que converte a falha no registro que vai para o acervo, já sem identificador.
    public static ArquivoIlegivel de(FalhaDeLeitura falha) {
        if (falha == null) {
            throw new IllegalArgumentException("Não há falha de leitura a converter.");
        }
        return new ArquivoIlegivel(
                semIdentificador(falha.origem()), falha.tipoDeErro(), falha.motivo());
    }

    // Método estático que devolve o nome como fica depois da limpeza; é visível para teste.
    static String semIdentificador(String origem) {
        return semCnpjDaChave(soONome(origem));
    }

    // Método auxiliar que tira a pasta e deixa só o nome do arquivo, ou o caminho dentro do pacote.
    private static String soONome(String origem) {
        int separador = origem.indexOf(SEPARADOR_DE_ENTRADA);
        if (separador >= 0) {
            // Entrada de pacote: o que vem depois já é relativo ao pacote.
            String entrada = origem.substring(separador + 1);
            return entrada.isBlank() ? origem.substring(0, separador) : entrada;
        }
        int ultimaBarra = Math.max(origem.lastIndexOf('/'), origem.lastIndexOf(BARRA_INVERTIDA));
        if (ultimaBarra < 0) {
            return origem;
        }
        String nome = origem.substring(ultimaBarra + 1);
        return nome.isBlank() ? origem.substring(0, ultimaBarra + 1) : nome;
    }

    // Método auxiliar que troca o CNPJ de cada chave de acesso do nome pela marca [cnpj-oculto].
    private static String semCnpjDaChave(String nome) {
        Matcher chaves = FORMA_DE_CHAVE.matcher(nome);
        StringBuilder limpo = new StringBuilder();
        while (chaves.find()) {
            String chave = chaves.group();
            String substituta = chave.substring(0, INICIO_DO_CNPJ)
                    + MARCA_DO_CNPJ
                    + chave.substring(FIM_DO_CNPJ);
            chaves.appendReplacement(limpo, Matcher.quoteReplacement(substituta));
        }
        chaves.appendTail(limpo);
        return limpo.toString();
    }
}
