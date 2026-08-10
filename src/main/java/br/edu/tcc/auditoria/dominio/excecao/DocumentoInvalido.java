package br.edu.tcc.auditoria.dominio.excecao;

/** Sinaliza tentativa de construir um documento sem os dados que o identificam. */
public class DocumentoInvalido extends ExcecaoDeDominio {

    public DocumentoInvalido(String mensagem) {
        super(mensagem);
    }
}
