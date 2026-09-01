package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

/** Papel de trabalho que não pode ser montado ou exportado como pedido. */
public class PapelDeTrabalhoInvalido extends RuntimeException {

    public PapelDeTrabalhoInvalido(String mensagem) {
        super(mensagem);
    }

    public PapelDeTrabalhoInvalido(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
