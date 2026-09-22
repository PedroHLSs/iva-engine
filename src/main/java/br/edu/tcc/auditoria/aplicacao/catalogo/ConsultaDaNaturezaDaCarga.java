package br.edu.tcc.auditoria.aplicacao.catalogo;

/**
 * Porta de leitura da procedência de uma carga, sem carregar a carga.
 *
 * <h2>Por que separada do provedor de catálogo</h2>
 *
 * <p>Carregar o catálogo traz milhares de registros para a memória, e vale a pena
 * quando se vai resolver tratamento. Não vale para desenhar uma faixa de aviso:
 * a procedência são quatro linhas, e toda tela de resultado precisa dela.</p>
 *
 * <p>A faixa precisa aparecer <strong>em toda tela de resultado</strong>, e não só
 * onde o catálogo é exibido. A situação de um produto — "possível divergência" —
 * foi produzida contra aquela carga: se ela era de demonstração, o apontamento é
 * de demonstração, e quem lê precisa saber disso na tela em que lê.</p>
 */
public interface ConsultaDaNaturezaDaCarga {

    /**
     * A procedência da carga daquela versão.
     *
     * <p>Nunca devolve nulo. Carga inexistente, ou gravada antes de a declaração
     * existir, devolve {@link NaturezaDaCarga#naoDeclarada()} — que a tela escreve
     * como procedência não declarada, e nunca como normativa.</p>
     */
    NaturezaDaCarga daVersao(String versao);
}
