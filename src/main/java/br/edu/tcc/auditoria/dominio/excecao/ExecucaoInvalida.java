package br.edu.tcc.auditoria.dominio.excecao;

/** Tentativa de registrar uma execução de auditoria sem o que a torna reproduzível. */
public class ExecucaoInvalida extends ExcecaoDeDominio {

    public ExecucaoInvalida(String mensagem) {
        super(mensagem);
    }
}
