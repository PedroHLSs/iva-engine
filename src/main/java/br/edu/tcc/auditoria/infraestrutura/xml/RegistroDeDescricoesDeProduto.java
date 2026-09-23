package br.edu.tcc.auditoria.infraestrutura.xml;

// Interface que recebe as descrições de produto encontradas na leitura de um lote. Na análise elas vão para o acervo, e o acumulador nasce e morre com cada análise, nunca vive o processo inteiro.
@FunctionalInterface
public interface RegistroDeDescricoesDeProduto {

    // Registro que descarta tudo, usado pela linha de comando, que não grava descrição.
    RegistroDeDescricoesDeProduto DESCARTA = lida -> { };

    // Recebe a descrição lida de um item.
    void registrar(DescricaoDeProdutoLida lida);
}
