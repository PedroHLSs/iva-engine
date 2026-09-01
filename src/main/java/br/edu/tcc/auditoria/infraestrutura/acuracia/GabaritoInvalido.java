package br.edu.tcc.auditoria.infraestrutura.acuracia;

/**
 * Sinaliza gabarito que não pôde ser lido.
 *
 * <p>A mensagem sempre indica o número da linha física do arquivo, pelo mesmo
 * motivo de {@code ImportacaoDeCatalogoInvalida}: o gabarito é escrito à mão por
 * quem opera o sistema, e "linha 7 sem rotulo_esperado" é acionável, "gabarito
 * inválido" não é.</p>
 *
 * <p>Vive na infraestrutura porque é falha de leitura de arquivo. Quando um
 * valor lido viola invariante do domínio — chave de acesso com 43 dígitos,
 * rótulo desconhecido —, a exceção de domínio original vai como causa, e a
 * mensagem daqui acrescenta a linha.</p>
 */
public class GabaritoInvalido extends RuntimeException {

    public GabaritoInvalido(String mensagem) {
        super(mensagem);
    }

    public GabaritoInvalido(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
