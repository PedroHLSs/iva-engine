package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.util.List;

//Interface responsável por ler um lote de documentos, fornecendo uma fonte de documentos e registrando arquivos ilegíveis.
public interface LeituraDeLote {

    //Retorna a fonte de documentos que está sendo lida.
    FonteDeLoteDeDocumentos fonte();

    //Retorna a lista de arquivos ilegíveis encontrados durante a leitura.
    List<ArquivoIlegivel> arquivosIlegiveis();
   
    //Retorna a descrição do produto para um item específico, identificado pelo seu hash.
    DescricaoDoProduto descricaoDe(HashDoItem hashDoItem);
}
