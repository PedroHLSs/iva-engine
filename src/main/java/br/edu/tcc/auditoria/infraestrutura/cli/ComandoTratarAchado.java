package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.tratativa.ServicoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.DecisaoDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// Classe do comando tratar-achado, que registra a decisão de uma pessoa sobre um apontamento, com justificativa obrigatória. A pessoa informa o identificador da listagem; a chave da tratativa sai do próprio apontamento.
@Component
class ComandoTratarAchado implements Comando {

    static final String NOME = "tratar-achado";

    private static final String OPCAO_ACHADO = "achado";
    private static final String OPCAO_DECISAO = "decisao";
    private static final String OPCAO_JUSTIFICATIVA = "justificativa";

    private final ServicoDeTratativa servico;
    private final Saida saida;

    // Construtor que recebe o serviço de tratativa e a saída.
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

    // Registra a decisão e a justificativa para o apontamento informado e mostra para que versão da regra ela vale.
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

    // Método auxiliar que converte o texto na decisão; recusa valor desconhecido.
    private static DecisaoDeTratativa decisao(String informada) {
        return Arrays.stream(DecisaoDeTratativa.values())
                .filter(decisao -> decisao.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new UsoInvalido(
                        "Decisão desconhecida: \"%s\". Valores aceitos: %s."
                                .formatted(informada, Arrays.toString(DecisaoDeTratativa.values()))));
    }
}
