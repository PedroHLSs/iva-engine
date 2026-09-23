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

// Classe que recusa a subida quando o sal atual não é o que produziu o acervo gravado, porque com outro sal o mesmo participante passaria a ter dois pseudônimos sem ninguém perceber; a tratativa não é afetada. Só diagnosticar-sal e recomecar-do-zero passam, e o guarda roda antes de LinhaDeComando.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GuardaDeSalNaSubida implements CommandLineRunner {

    // Os únicos comandos que passam pelo guarda; comando novo nasce barrado.
    private static final Set<String> COMANDOS_PERMITIDOS =
            Set.of(ComandoDiagnosticarSal.NOME, ComandoRecomecarDoZero.NOME);

    private final SalResolvido salResolvido;
    private final RegistroDaImpressaoDigital registro;
    private final AcervoSalgado acervo;
    private final Saida saida;

    // Construtor que recebe o sal resolvido, o registro da impressão digital, o acervo e a saída.
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

    // Deixa passar a chamada sem comando e os comandos permitidos; os outros passam pela conferência.
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

    // Compara a impressão digital em uso com a gravada: grava se não houver, aceita se forem iguais ou se o banco estiver vazio, e recusa nos outros casos.
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
            // Sem documento gravado, nenhum pseudônimo fica órfão: passa a valer o sal novo, e isso é avisado.
            registro.registrar(emUso, salResolvido.origem(), false);
            saida.linha("O sal mudou, e o acervo estava vazio: nenhum pseudônimo foi orfanado.");
            saida.linha("Impressão digital agora em uso: %s", emUso.valor());
            return;
        }
        recusar(emUso, gravada.get(), documentos);
    }

    // Método auxiliar que registra a impressão digital pela primeira vez. Se o banco já tinha documentos, não há com o que comparar, e isso fica gravado para o diagnóstico não afirmar uma checagem que não houve.
    private void adotar(ImpressaoDigitalDoSal emUso, long documentos) {
        registro.registrar(emUso, salResolvido.origem(), documentos > 0);
        if (documentos > 0) {
            saida.linha("Primeira conferência de sal neste acervo: a impressão digital foi "
                    + "registrada agora, sobre %d documento(s) que já existiam.", documentos);
            saida.linha("Não houve o que verificar desta vez — não havia impressão digital "
                    + "anterior. A partir daqui, trocar o sal passa a ser recusado.");
        }
    }

    // Método auxiliar que recusa a subida, explicando o que aconteceu e as duas saídas, sem mostrar o sal.
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

        // Sem rastro de pilha: é uma decisão, não uma queda, e as linhas acima já explicam o que fazer.
        recusa.setStackTrace(new StackTraceElement[0]);
        throw recusa;
    }
}
