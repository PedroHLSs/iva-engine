package br.edu.tcc.auditoria.aplicacao.conferencia;

// Classe que decide o que a tela mostra recolhido. A regra, em uma frase: nasce recolhida somente a explicação de uma verificação cujo estado é SEM_DIVERGENCIA_IDENTIFICADA; toda outra nasce aberta, e nenhum agrupamento nasce recolhido se contiver ao menos uma verificação que não seja SEM_DIVERGENCIA_IDENTIFICADA. É lista de permissão com um valor só: estado novo nasce aberto. A tela só obedece; não decide. Acrescentada na Etapa 13 (D014).
public final class RegraDeColapso {

    // Construtor privado: ninguém cria objeto desta classe, só usa os métodos estáticos.
    private RegraDeColapso() {
    }

    // Método estático que diz se a explicação de uma verificação nasce recolhida.
    public static boolean verificacaoNasceRecolhida(EstadoDeConferencia estado) {
        return estado == EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
    }

    // Método estático que diz se um agrupamento nasce recolhido: só quando todas as verificações dele estão sem divergência e há pelo menos uma.
    public static boolean agrupamentoNasceRecolhido(ContagemDeEstados verificacoes) {
        if (verificacoes == null || verificacoes.total() == 0) {
            return false;
        }
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            if (!verificacaoNasceRecolhida(estado) && verificacoes.quantidadeDe(estado) > 0) {
                return false;
            }
        }
        return true;
    }
}
