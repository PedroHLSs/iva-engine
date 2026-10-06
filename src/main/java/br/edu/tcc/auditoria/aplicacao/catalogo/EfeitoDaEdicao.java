package br.edu.tcc.auditoria.aplicacao.catalogo;

// Enum que diz o que "salvar" faz com uma carga editada. Depende só de a carga já ter sido entregue a uma análise.
public enum EfeitoDaEdicao {

    // A carga nunca foi usada: é rascunho, e a edição muda a própria carga.
    ALTERAR_RASCUNHO,

    // A carga já foi usada e está selada: a edição cria uma carga nova, e a original fica como está.
    CRIAR_VERSAO_NOVA
}
