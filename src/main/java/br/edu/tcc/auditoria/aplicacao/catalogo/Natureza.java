package br.edu.tcc.auditoria.aplicacao.catalogo;

/**
 * O que uma tabela da carga é: dado real ou dado de demonstração.
 *
 * <h2>Isto é fato sobre o arquivo, não configuração de quem roda</h2>
 *
 * <p>A alternativa considerada e recusada era uma propriedade de instalação do
 * tipo {@code auditoria.demonstracao=true}. Ela é promessa de quem configurou:
 * se alguém esquecer de ligá-la, a tela apresenta dado fictício como se fosse
 * norma vigente — que é exatamente o modo de falha que a marcação existe para
 * evitar. Aqui a natureza viaja com o dado, declarada linha a linha no CSV,
 * persistida junto da carga, e ninguém consegue esquecer de ligar.</p>
 *
 * <h2>Por que coluna, e não cabeçalho de comentário</h2>
 *
 * <p>Os CSV fictícios já traziam {@code # ATENCAO: DADOS INTEIRAMENTE FICTICIOS}
 * no topo, e a primeira ideia foi transportar isso até a carga. Não é viável por
 * dois motivos, e o segundo é decisivo: {@code LeitorCsv} descarta as linhas de
 * comentário antes de qualquer chamador vê-las, e — mesmo que não descartasse —
 * <strong>ausência de prosa é indistinguível de prosa que foi apagada</strong>.
 * Um arquivo real e um arquivo fictício de onde alguém tirou o aviso chegariam
 * iguais. A coluna obrigatória não tem esse buraco: linha sem natureza recusa a
 * importação inteira.</p>
 *
 * <p>Nenhuma destas constantes é conteúdo normativo. Elas dizem de onde o dado
 * veio, não o que a lei diz.</p>
 */
public enum Natureza {

    /** Dado inventado para demonstração. Não pode ser lido como afirmação sobre a lei. */
    FICTICIO("fictício"),

    /** Dado transcrito de fonte normativa por quem montou a carga. */
    NORMATIVO("normativo");

    private final String rotulo;

    Natureza(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Como a natureza se escreve na tela. */
    public String rotulo() {
        return rotulo;
    }
}
