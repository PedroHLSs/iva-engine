package br.edu.tcc.auditoria.aplicacao.catalogo;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// Interface responsável por consultar, travar, editar e excluir cargas de catálogo. Quem implementa garante no banco o que a aplicação decide aqui: carga selada não muda nem é excluída.
public interface AcervoDeCargas {

    // Lista todas as cargas, da mais recente para a mais antiga.
    List<EstadoDaCarga> listar();

    // Devolve o estado da carga daquela versão.
    Optional<EstadoDaCarga> estado(String versao);

    // Devolve o conteúdo inteiro da carga, para servir de origem a uma edição.
    Optional<CargaDeCatalogo> conteudo(String versao);

    // Diz se já existe carga com a versão.
    boolean existeVersao(String versao);

    // Roda a operação com a linha da carga travada, numa transação só. É a mesma trava que a entrega ao motor usa para selar, e é isso que impede editar um rascunho enquanto uma análise o lê.
    <T> T comCargaTravada(String versao, Function<EstadoDaCarga, T> operacao);

    // Roda a operação com o acervo inteiro travado, numa transação só, e entrega a carga mais recente lida depois de travar, ou vazio se não há nenhuma. Acrescentado em 04/10/2026 (D026). Todo caminho que muda qual é a carga mais recente, ou o conteúdo de uma carga que pode servir de origem, passa por aqui: importação completa e parcial, edição nos dois efeitos e exclusão. A ordem é sempre esta trava primeiro e, dentro dela, a trava da linha. A selagem não a toma: ela só trava a linha da mais recente, e por isso não há espera em ciclo.
    <T> T comAcervoTravado(Function<Optional<EstadoDaCarga>, T> operacao);

    // Troca o conteúdo de um rascunho, mantendo a versão. Recusa carga selada.
    void substituirConteudo(String versao, CargaDeCatalogo nova);

    // Grava uma carga nova derivada de outra, que fica intacta.
    void salvarDerivada(CargaDeCatalogo nova, String versaoDeOrigem);

    // Exclui um rascunho. Recusa carga selada.
    void excluir(String versao);
}
