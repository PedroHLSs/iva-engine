package br.edu.tcc.auditoria.aplicacao.catalogo;

// Interface responsável por consultar a procedência de uma carga sem carregar o catálogo inteiro, para a faixa de aviso das telas de resultado.
public interface ConsultaDaNaturezaDaCarga {

    // Retorna a procedência da carga daquela versão; carga inexistente ou anterior à declaração volta como não declarada, nunca nula.
    NaturezaDaCarga daVersao(String versao);
}
