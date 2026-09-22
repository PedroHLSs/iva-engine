package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Apaga o acervo que dependia do sal antigo e adota o sal atual.
 *
 * <h2>Para o caso legítimo</h2>
 *
 * <p>Trocar de sal de propósito é uma decisão defensável — máquina nova, segredo
 * comprometido, instalação recomeçada. O que não é defensável é o sistema decidir
 * sozinho que era isso. Este comando é o lugar onde a pessoa afirma que era.</p>
 *
 * <h2>O que ele NÃO apaga</h2>
 *
 * <p>Tratativas. A chave delas é o hash do item, que não leva sal: a decisão
 * humana continua válida e se reaplica sozinha quando o lote for reprocessado.
 * Apagá-las aqui destruiria trabalho de auditoria que a troca de sal nunca
 * tocou.</p>
 *
 * <p>Catálogo também fica: não tem sal nenhum, e reimportá-lo à toa só criaria
 * uma versão de carga nova sem motivo.</p>
 *
 * <h2>Por que a confirmação é uma palavra, e não uma marca</h2>
 *
 * <p>{@code --confirmo=sim}, e não {@code --confirmo}. O interpretador de
 * argumentos trata opção sem valor como opção em branco, então uma marca solta
 * não chegaria aqui distinguível de ausência. Exigir a palavra resolve isso e, de
 * quebra, torna o comando difícil de rodar por engano ao repetir uma linha do
 * histórico.</p>
 */
@Component
class ComandoRecomecarDoZero implements Comando {

    static final String NOME = "recomecar-do-zero";

    static final String OPCAO_CONFIRMO = "confirmo";

    static final String CONFIRMACAO = "sim";

    private final AcervoSalgado acervo;
    private final RegistroDaImpressaoDigital registro;
    private final SalResolvido salResolvido;
    private final Saida saida;

    ComandoRecomecarDoZero(
            AcervoSalgado acervo,
            RegistroDaImpressaoDigital registro,
            SalResolvido salResolvido,
            Saida saida) {

        this.acervo = acervo;
        this.registro = registro;
        this.salResolvido = salResolvido;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Apaga o acervo gravado e adota o sal atual. Preserva as tratativas.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --%s=%s

                  --%s  confirmação explícita, com esta palavra. Sem ela o comando
                              não faz nada.

                  Apaga documentos, itens, execuções e apontamentos, e passa a
                  registrar o sal atual como o do acervo. Use quando a troca de sal
                  foi intencional.

                  NÃO apaga as tratativas: a chave delas é o hash do item, que não
                  leva sal, e elas voltam a valer quando o lote for reprocessado.
                  NÃO apaga o catálogo importado, que não tem sal.
                """.formatted(NOME, OPCAO_CONFIRMO, CONFIRMACAO, OPCAO_CONFIRMO);
    }

    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_CONFIRMO));

        String confirmacao = argumentos.texto(OPCAO_CONFIRMO).orElse("");
        if (!CONFIRMACAO.equals(confirmacao)) {
            throw new UsoInvalido(
                    ("Este comando apaga o acervo gravado. Para confirmar, rode \"%s --%s=%s\". "
                            + "Nada foi apagado.").formatted(NOME, OPCAO_CONFIRMO, CONFIRMACAO));
        }

        long documentosAntes = acervo.quantidadeDeDocumentos();
        AcervoSalgado.Apagamento apagamento = acervo.apagar();
        registro.registrar(salResolvido.impressaoDigital(), salResolvido.origem(), false);

        saida.linha("Acervo recomeçado.");
        saida.linha("  documentos apagados .......... %d", apagamento.documentos());
        saida.linha("  execuções apagadas ........... %d", apagamento.execucoes());
        saida.linha("  tratativas preservadas ....... %d", apagamento.tratativasPreservadas());
        saida.linhaEmBranco();
        saida.linha("Impressão digital agora em uso: %s",
                salResolvido.impressaoDigital().valor());
        saida.linha("Sal resolvido de: %s", salResolvido.origem().descricao());
        saida.linhaEmBranco();
        if (documentosAntes > 0) {
            saida.linha("Reprocesse o lote com \"%s\". As %d tratativa(s) preservada(s) se reaplicam "
                    + "sozinhas aos itens que continuarem iguais.",
                    ComandoAuditar.NOME, apagamento.tratativasPreservadas());
        }
    }
}
