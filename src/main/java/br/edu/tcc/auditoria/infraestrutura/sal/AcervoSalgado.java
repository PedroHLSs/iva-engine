package br.edu.tcc.auditoria.infraestrutura.sal;

// Interface que representa o que no banco depende do sal: os documentos, e com eles as execuções e os apontamentos. A tratativa não leva sal e nunca é apagada, porque a chave dela é o hash do item, que não usa sal.
public interface AcervoSalgado {

    // Retorna quantos documentos existem; é o que diz se o banco já tem dados. O catálogo não conta, porque não tem sal.
    long quantidadeDeDocumentos();

    // Apaga documentos, itens, execuções e apontamentos, e preserva as tratativas; devolve o que foi apagado.
    Apagamento apagar();

    // Representa o que o apagamento removeu e quantas tratativas ficaram.
    record Apagamento(long documentos, long execucoes, long tratativasPreservadas) {
    }
}
