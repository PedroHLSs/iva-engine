package br.edu.tcc.auditoria.infraestrutura.xml;

/**
 * Destino das descrições de produto encontradas durante a leitura de um lote.
 *
 * <p>Existe pela mesma razão de {@link RegistroDeFalhasDeLeitura}: o leitor
 * devolve um fluxo, e um fluxo devolve uma coisa só. A descrição precisa de outra
 * saída, e o leitor não pode decidir qual — numa análise ela vai para o acervo,
 * na linha de comando não vai para lugar nenhum.</p>
 *
 * <h2>Escopo de análise, nunca de processo</h2>
 *
 * <p>Quem acumula é {@link DescricoesDeProdutoEmMemoria}, e ele nasce e morre
 * dentro de uma análise. Não existe acumulador compartilhado: um registro vivo
 * enquanto o processo vive faria a segunda análise enxergar descrições da
 * primeira, que é a mesma família de defeito que o registro de falhas da Etapa 4
 * teve de resolver quando a API passou a existir.</p>
 */
@FunctionalInterface
public interface RegistroDeDescricoesDeProduto {

    /**
     * Sumidouro para quem lê sem gravar acervo.
     *
     * <p>É o caso da linha de comando: {@code auditar} não escreve
     * {@code item_da_execucao}, então não há linha onde a descrição caberia.
     * Guardá-la em memória ali seria acumular, pelo tempo do processo, um texto
     * que ninguém vai ler — exatamente o que esta etapa não quer.</p>
     */
    RegistroDeDescricoesDeProduto DESCARTA = lida -> { };

    void registrar(DescricaoDeProdutoLida lida);
}
