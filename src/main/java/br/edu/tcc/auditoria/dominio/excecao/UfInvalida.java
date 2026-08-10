package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza sigla que não corresponde a nenhuma unidade federativa conhecida. */
public class UfInvalida extends ExcecaoDeDominio {

    public UfInvalida(String mensagem) {
        super(mensagem);
    }
}
