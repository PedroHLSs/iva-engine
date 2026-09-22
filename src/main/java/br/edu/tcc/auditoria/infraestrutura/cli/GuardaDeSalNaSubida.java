package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.infraestrutura.sal.AcervoSalgado;
import br.edu.tcc.auditoria.infraestrutura.sal.ImpressaoDigitalDoSal;
import br.edu.tcc.auditoria.infraestrutura.sal.RegistroDaImpressaoDigital;
import br.edu.tcc.auditoria.infraestrutura.sal.SalResolvido;
import br.edu.tcc.auditoria.infraestrutura.sal.SalTrocado;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Recusa a subida quando o sal resolvido não é o que produziu o acervo gravado.
 *
 * <h2>O que a troca de sal quebra — e o que ela não quebra</h2>
 *
 * <p><strong>Não quebra tratativa.</strong> A chave da tratativa é
 * {@code (hash do item, regra, versão da regra)}, e o hash do item não leva sal,
 * por decisão explícita: ele é identidade reproduzível entre instalações, não
 * sigilo. Decisão humana registrada sobrevive à troca e se reaplica sozinha no
 * reprocessamento.</p>
 *
 * <p><strong>Quebra a coerência interna do acervo.</strong>
 * {@code emitente_pseudonimizado} e {@code destinatario_pseudonimizado} saíram do
 * sal anterior. Com sal novo, o mesmo participante passa a existir sob dois
 * pseudônimos no mesmo acervo, e o pseudônimo do documento deixa de bater com o
 * que já saiu em planilha e em API.</p>
 *
 * <p>E esse é o defeito pior, não o mais brando: tratativa reaberta o usuário
 * vê. Participante duplicado não aparece em lugar nenhum — nenhuma contagem
 * muda, nenhum relatório acusa, e o acervo fica incoerente com aparência
 * normal. É a mesma família do "R04: 0 apontamentos" que não se distinguia de
 * "R04: não avaliado": falso negativo silencioso.</p>
 *
 * <h2>Recusa por padrão, permissão explícita</h2>
 *
 * <p>A lista abaixo é de quem PASSA, não de quem é barrado. Um comando novo
 * nasce protegido: para atravessar o guarda é preciso acrescentá-lo aqui, de
 * propósito. A regra inversa — enumerar quem é barrado — deixaria o comando
 * esquecido na lista rodando contra pseudônimo incoerente, e lista de exclusão é
 * exatamente o tipo de coisa que ninguém revisita.</p>
 *
 * <p>Os dois que passam são os que existem para resolver justamente esta
 * situação. Barrá-los deixaria a pessoa com um sistema que se recusa a subir e
 * nenhuma ferramenta para entender por quê.</p>
 *
 * <h2>Por que roda antes de {@code LinhaDeComando}</h2>
 *
 * <p>Os dois são {@code CommandLineRunner}. Este declara precedência máxima e
 * aquele não declara nenhuma, então o Spring executa este primeiro. Foi o jeito
 * de interceptar todo comando sem alterar {@code LinhaDeComando}, que é da
 * Etapa 5.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GuardaDeSalNaSubida implements CommandLineRunner {

    /** Quem atravessa o guarda. Acrescentar aqui é decisão, não manutenção. */
    private static final Set<String> COMANDOS_PERMITIDOS =
            Set.of(ComandoDiagnosticarSal.NOME, ComandoRecomecarDoZero.NOME);

    private final SalResolvido salResolvido;
    private final RegistroDaImpressaoDigital registro;
    private final AcervoSalgado acervo;
    private final Saida saida;

    GuardaDeSalNaSubida(
            SalResolvido salResolvido,
            RegistroDaImpressaoDigital registro,
            AcervoSalgado acervo,
            Saida saida) {

        this.salResolvido = salResolvido;
        this.registro = registro;
        this.acervo = acervo;
        this.saida = saida;
    }

    @Override
    public void run(String... argumentos) {
        if (argumentos.length == 0) {
            // Sem comando não há o que proteger: LinhaDeComando imprime o uso.
            return;
        }
        if (COMANDOS_PERMITIDOS.contains(argumentos[0])) {
            return;
        }
        conferir();
    }

    void conferir() {
        ImpressaoDigitalDoSal emUso = salResolvido.impressaoDigital();
        Optional<RegistroDaImpressaoDigital.Registro> gravada = registro.registrada();
        long documentos = acervo.quantidadeDeDocumentos();

        if (gravada.isEmpty()) {
            adotar(emUso, documentos);
            return;
        }
        if (gravada.get().impressao().equals(emUso)) {
            return;
        }
        if (documentos == 0) {
            // Sem documento gravado não há pseudônimo a orfanar. A impressão
            // digital passa a ser a do sal novo, e isso é dito em voz alta.
            registro.registrar(emUso, salResolvido.origem(), false);
            saida.linha("O sal mudou, e o acervo estava vazio: nenhum pseudônimo foi orfanado.");
            saida.linha("Impressão digital agora em uso: %s", emUso.valor());
            return;
        }
        recusar(emUso, gravada.get(), documentos);
    }

    /**
     * Registra a impressão digital pela primeira vez.
     *
     * <p>Com acervo já povoado não há o que conferir: a tabela do guarda não
     * existia quando aqueles pseudônimos foram gravados, e não há com o que
     * comparar. O sal atual é assumido como o correto, e a ressalva fica
     * gravada — o diagnóstico não vai afirmar uma verificação que não houve.</p>
     */
    private void adotar(ImpressaoDigitalDoSal emUso, long documentos) {
        registro.registrar(emUso, salResolvido.origem(), documentos > 0);
        if (documentos > 0) {
            saida.linha("Primeira conferência de sal neste acervo: a impressão digital foi "
                    + "registrada agora, sobre %d documento(s) que já existiam.", documentos);
            saida.linha("Não houve o que verificar desta vez — não havia impressão digital "
                    + "anterior. A partir daqui, trocar o sal passa a ser recusado.");
        }
    }

    /**
     * Recusa a subida, dizendo o que aconteceu e quais são as duas saídas.
     *
     * <p>A mensagem sai pela {@link Saida} antes da exceção porque é ela que a
     * pessoa precisa ler. Nenhum sal aparece: as duas impressões digitais bastam
     * para comparar, e são o que se pode mostrar sem expor segredo.</p>
     */
    private void recusar(
            ImpressaoDigitalDoSal emUso,
            RegistroDaImpressaoDigital.Registro gravada,
            long documentos) {

        saida.linhaEmBranco();
        saida.linha("RECUSADO: o sal de pseudonimização não é o que produziu este acervo.");
        saida.linhaEmBranco();
        saida.linha("  impressão digital gravada .. %s", gravada.impressao().valor());
        saida.linha("  impressão digital em uso ... %s", emUso.valor());
        saida.linha("  sal em uso veio de ......... %s", salResolvido.origem().descricao());
        salResolvido.arquivo().ifPresent(caminho -> saida.linha("  arquivo .................... %s", caminho));
        saida.linhaEmBranco();
        saida.linha("Há %d documento(s) gravado(s). Os campos emitente_pseudonimizado e", documentos);
        saida.linha("destinatario_pseudonimizado deles foram calculados com o outro sal.");
        saida.linha("Prosseguir não daria erro nenhum: o mesmo participante passaria a existir");
        saida.linha("sob dois pseudônimos no mesmo acervo, e o pseudônimo do documento deixaria");
        saida.linha("de bater com o que já saiu em planilha e em API. Nenhuma contagem mudaria e");
        saida.linha("nenhum relatório acusaria — por isso a recusa é aqui, e não um aviso.");
        saida.linhaEmBranco();
        saida.linha("As tratativas NÃO estão em risco: a chave delas é o hash do item, que não");
        saida.linha("leva sal, e volta a valer sozinha quando o lote for reprocessado.");
        saida.linhaEmBranco();
        saida.linha("Duas saídas:");
        saida.linha("  1. Reponha o sal anterior — %s confirma qual está em uso sem mostrá-lo.",
                ComandoDiagnosticarSal.NOME);
        saida.linha("  2. Se recomeçar o acervo for mesmo a intenção: %s --%s=%s",
                ComandoRecomecarDoZero.NOME,
                ComandoRecomecarDoZero.OPCAO_CONFIRMO,
                ComandoRecomecarDoZero.CONFIRMACAO);
        saida.linhaEmBranco();

        SalTrocado recusa = new SalTrocado(
                ("O sal de pseudonimização não corresponde ao acervo gravado: impressão digital em uso "
                        + "%s, gravada %s, com %d documento(s) no banco. Ver a explicação acima.")
                        .formatted(emUso.abreviada(), gravada.impressao().abreviada(), documentos));

        // Sem rastro de pilha: esta recusa é uma decisão, não uma queda. As linhas
        // acima já dizem o que aconteceu e o que fazer, e um rastro de pilha atrás
        // delas faria a recusa parecer defeito do sistema — justamente na hora em
        // que a pessoa precisa acreditar no que leu. A pilha não informaria nada:
        // só existe um lugar que lança isto.
        recusa.setStackTrace(new StackTraceElement[0]);
        throw recusa;
    }
}
