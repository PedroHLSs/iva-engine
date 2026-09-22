package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte a falha de leitura registrada pelo leitor no registro que a análise
 * guarda — tirando do caminho o que não pode ser gravado.
 *
 * <h2>Duas coisas saem do nome</h2>
 *
 * <p><strong>O diretório.</strong> {@code FalhaDeLeitura} registra o caminho
 * completo, que numa máquina de verdade começa por {@code C:\Users\<pessoa>} e
 * num servidor aponta para o diretório temporário do processo. Nenhum dos dois
 * ajuda quem lê o relatório, e o primeiro é dado pessoal de quem rodou. Fica o
 * nome do arquivo; de entrada de pacote fica o caminho relativo dentro dele, que
 * já é curto e distingue arquivos de mesmo nome em pastas diferentes.</p>
 *
 * <p><strong>O CNPJ do emitente.</strong> A maioria dos ERP exporta NF-e com o
 * nome {@code <chave de acesso>-nfe.xml}, e as posições 7 a 20 da chave são o
 * CNPJ de quem emitiu (D005). Elas são substituídas por uma marca escrita; o
 * resto da chave permanece.</p>
 *
 * <h2>Por que não pseudonimizar a chave inteira</h2>
 *
 * <p>Porque o único uso deste campo é a pessoa achar o arquivo para consertá-lo,
 * e um pseudônimo de 64 caracteres hexadecimais não corresponde a nada no disco
 * dela. O que sobra da chave — UF, ano e mês, modelo, série, número e código
 * numérico — localiza o arquivo sem identificar ninguém, que é exatamente a
 * mesma leitura que a D007 e a D009 já fazem ao publicar modelo, série, número,
 * data e UF do documento.</p>
 *
 * <p>Nota honesta sobre o alcance: isto protege o <em>emitente</em>, que é o que
 * a chave carrega por definição do leiaute. Um nome de arquivo que traga razão
 * social ou nome de cliente escrito à mão passa — nenhuma regra de forma pega
 * texto livre. É a mesma fronteira que a descrição do produto encontra, e por
 * isso ela tem regime próprio.</p>
 */
public final class OrigemDeArquivoIlegivel {

    /** Separador que {@code LeitorLote} usa entre o pacote e a entrada. */
    private static final char SEPARADOR_DE_ENTRADA = '!';

    /** Separador de caminho do Windows, escrito pelo ponto de código para o
     * arquivo não depender de escape. */
    private static final char BARRA_INVERTIDA = (char) 92;

    /** Qualquer corrida de 44 dígitos: é a forma de uma chave de acesso. */
    private static final Pattern FORMA_DE_CHAVE = Pattern.compile("[0-9]{44}");

    /** Posições da chave, base zero, em que mora o CNPJ do emitente. */
    private static final int INICIO_DO_CNPJ = 6;
    private static final int FIM_DO_CNPJ = 20;

    static final String MARCA_DO_CNPJ = "[cnpj-oculto]";

    private OrigemDeArquivoIlegivel() {
    }

    /** O registro que vai para o acervo da análise, já sem identificador. */
    public static ArquivoIlegivel de(FalhaDeLeitura falha) {
        if (falha == null) {
            throw new IllegalArgumentException("Não há falha de leitura a converter.");
        }
        return new ArquivoIlegivel(
                semIdentificador(falha.origem()), falha.tipoDeErro(), falha.motivo());
    }

    /** Visível para teste: o nome como ele fica depois da limpeza. */
    static String semIdentificador(String origem) {
        return semCnpjDaChave(soONome(origem));
    }

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
