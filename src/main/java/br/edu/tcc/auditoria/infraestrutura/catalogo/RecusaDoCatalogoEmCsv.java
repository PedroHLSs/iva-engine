package br.edu.tcc.auditoria.infraestrutura.catalogo;

import br.edu.tcc.auditoria.infraestrutura.csv.RecusaPorCampo;

// Classe que diz ao leitor de CSV como recusar um arquivo de catálogo: com a coluna e o valor quando o problema é de um campo, e como problema do arquivo quando falta coluna no cabeçalho. Acrescentada na Etapa 12.
final class RecusaDoCatalogoEmCsv implements RecusaPorCampo {

    static final RecusaDoCatalogoEmCsv INSTANCIA = new RecusaDoCatalogoEmCsv();

    // Construtor privado: há uma instância só.
    private RecusaDoCatalogoEmCsv() {
    }

    @Override
    public RuntimeException de(String mensagem, Throwable causa) {
        return new ImportacaoDeCatalogoInvalida(mensagem, causa);
    }

    @Override
    public RuntimeException deCampo(
            int linha, String coluna, String valor, String mensagem, Throwable causa) {
        return new RecusaDeCampo(linha, coluna, valor, mensagem, causa);
    }

    @Override
    public RuntimeException deColunaAusente(int linha, String coluna, String mensagem) {
        return new RecusaDeCabecalho(
                "o arquivo não tem a coluna \"%s\", obrigatória.".formatted(coluna));
    }
}
