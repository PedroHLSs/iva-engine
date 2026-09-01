package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Comando {@code tratar-achado}: registra a decisão de uma pessoa sobre um
 * apontamento.
 *
 * <p>A justificativa é obrigatória. O comando não tem opção para omiti-la, e o
 * domínio recusaria texto em branco de qualquer forma: apontamento tratado sem
 * razão registrada é apontamento apagado, e o relatório deixaria de dizer por
 * que a incoerência não é mais incoerência.</p>
 *
 * <p>Quem trata informa o identificador que a listagem mostra. A chave da
 * tratativa — resumo do item, regra e versão da regra — é derivada do
 * apontamento, e não digitada.</p>
 */
@Component
class ComandoTratarAchado implements Comando {

    static final String NOME = "tratar-achado";

    private static final String OPCAO_ACHADO = "achado";
    private static final String OPCAO_DECISAO = "decisao";
    private static final String OPCAO_JUSTIFICATIVA = "justificativa";

    private final ServicoDeTratativa servico;
    private final Saida saida;

    ComandoTratarAchado(ServicoDeTratativa servico, Saida saida) {
        this.servico = servico;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Aceita ou refuta um apontamento, com justificativa.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --achado=<id> --decisao=<%s> --justificativa=<texto>

                  --achado         identificador do apontamento, como sai em "%s"
                  --decisao        %s se a incoerência procede,
                                   %s se o documento está correto
                  --justificativa  por que, em texto livre. Obrigatória.

                A tratativa vale para esta versão da regra. Se a regra mudar de
                versão, o apontamento reabre e precisa de decisão nova, porque o
                critério que o gerou passou a ser outro.
                """.formatted(
                        NOME,
                        String.join("|",
                                Arrays.stream(DecisaoDeTratativa.values()).map(Enum::name).toList()),
                        ComandoListarAchados.NOME,
                        DecisaoDeTratativa.ACEITO,
                        DecisaoDeTratativa.REFUTADO);
    }

    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_ACHADO, OPCAO_DECISAO, OPCAO_JUSTIFICATIVA));

        UUID achadoId = argumentos.identificadorObrigatorio(OPCAO_ACHADO);
        DecisaoDeTratativa decisao = decisao(argumentos.textoObrigatorio(OPCAO_DECISAO));
        String justificativa = argumentos.textoObrigatorio(OPCAO_JUSTIFICATIVA);

        Tratativa tratativa = servico.registrar(achadoId, decisao, justificativa);

        saida.linha("Apontamento %s marcado como %s.", achadoId, tratativa.decisao());
        saida.linha("  justificativa: %s", tratativa.justificativa());
        saida.linha("  vale para a regra %s na versão %s.",
                tratativa.regraId(), tratativa.regraVersao());
    }

    private static DecisaoDeTratativa decisao(String informada) {
        return Arrays.stream(DecisaoDeTratativa.values())
                .filter(decisao -> decisao.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new UsoInvalido(
                        "Decisão desconhecida: \"%s\". Valores aceitos: %s."
                                .formatted(informada, Arrays.toString(DecisaoDeTratativa.values()))));
    }
}
