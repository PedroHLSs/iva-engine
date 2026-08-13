package br.edu.tcc.auditoria.infraestrutura.catalogo;

/**
 * Sinaliza CSV de catálogo que não pôde ser importado.
 *
 * <p>A mensagem sempre indica o número da linha física do arquivo. Catálogo é
 * carregado por quem opera o sistema, não por quem o escreveu: "linha 7 sem
 * fonteNormativa" é acionável, "erro de importação" não é.</p>
 *
 * <p>Vive na infraestrutura porque é falha de leitura de arquivo, não de regra
 * de negócio. Quando um valor lido viola invariante do domínio, a exceção de
 * domínio original vai como causa, e a mensagem daqui acrescenta a linha.</p>
 */
public class ImportacaoDeCatalogoInvalida extends RuntimeException {

    public ImportacaoDeCatalogoInvalida(String mensagem) {
        super(mensagem);
    }

    public ImportacaoDeCatalogoInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
