package br.edu.tcc.auditoria.infraestrutura.xml;

/**
 * O conteúdo lido não pôde ser transformado num documento fiscal.
 *
 * <p>Cobre tanto o XML que não abre — malformado, truncado, com raiz que não é
 * de NF-e — quanto o que abre mas não traz o que o modelo de domínio exige, como
 * documento sem chave de acesso ou sem emitente.</p>
 *
 * <p>É exceção de infraestrutura, e não de domínio: o problema está no arquivo
 * de entrada, não numa regra de negócio. Num lote, ela não interrompe o
 * processamento — {@link LeitorLote} a converte em {@link FalhaDeLeitura} e
 * segue para o arquivo seguinte.</p>
 *
 * <p>A mensagem nunca reproduz conteúdo do documento além do estritamente
 * necessário para localizar o erro: o arquivo pode ser uma nota real.</p>
 */
public class DocumentoFiscalIlegivel extends RuntimeException {

    public DocumentoFiscalIlegivel(String mensagem) {
        super(mensagem);
    }

    public DocumentoFiscalIlegivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
