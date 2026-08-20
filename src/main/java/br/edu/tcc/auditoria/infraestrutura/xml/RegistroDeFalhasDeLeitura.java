package br.edu.tcc.auditoria.infraestrutura.xml;

/**
 * Destino das falhas encontradas durante a leitura de um lote.
 *
 * <p>Existe como interface porque o {@link LeitorLote} devolve um fluxo, e um
 * fluxo só devolve uma coisa. As falhas precisam de outra saída, e essa saída
 * não pode ser decidida pelo leitor: numa execução de linha de comando o destino
 * é a tela, num relatório é uma seção, num teste é uma lista.</p>
 *
 * <p>Implementações podem ser chamadas enquanto o fluxo é consumido, e
 * {@link FalhasDeLeituraEmMemoria} é a implementação de referência.</p>
 */
@FunctionalInterface
public interface RegistroDeFalhasDeLeitura {

    void registrar(FalhaDeLeitura falha);
}
