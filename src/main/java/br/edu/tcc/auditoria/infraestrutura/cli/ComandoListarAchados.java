package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.dominio.Achado;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Classe do comando listar-achados, que mostra os apontamentos gravados, já com a tratativa. Apontamento tratado continua aparecendo; quem quiser só os que faltam decidir usa --apenas-abertos.
@Component
class ComandoListarAchados implements Comando {

    static final String NOME = "listar-achados";

    private static final String OPCAO_SEVERIDADE = "severidade";
    private static final String OPCAO_REGRA = "regra";
    private static final String OPCAO_CHAVE = "chave";
    private static final String OPCAO_APENAS_ABERTOS = "apenas-abertos";
    private static final String OPCAO_LIMITE = "limite";

    private final ConsultaDeAchados consulta;
    private final Saida saida;

    // Construtor que recebe a consulta de achados e a saída.
    ComandoListarAchados(ConsultaDeAchados consulta, Saida saida) {
        this.consulta = consulta;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Lista os apontamentos gravados, do mais grave para o menos grave.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s [--severidade=<%s>] [--regra=<id>] [--chave=<44 dígitos>]
                               [--apenas-abertos] [--limite=<n>]

                  --severidade      mostra só apontamentos dessa gravidade
                  --regra           mostra só apontamentos dessa regra, por exemplo R05
                  --chave           mostra só apontamentos desse documento
                  --apenas-abertos  omite os que já têm tratativa aplicável
                  --limite          quantos apontamentos listar (padrão: %d)
                """.formatted(
                        NOME,
                        String.join("|", Arrays.stream(Severidade.values()).map(Enum::name).toList()),
                        FiltroDeAchados.LIMITE_PADRAO);
    }

    // Lista os apontamentos que atendem aos filtros e mostra quantos há no total.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(
                OPCAO_SEVERIDADE, OPCAO_REGRA, OPCAO_CHAVE, OPCAO_APENAS_ABERTOS, OPCAO_LIMITE));

        FiltroDeAchados filtro = new FiltroDeAchados(
                argumentos.texto(OPCAO_SEVERIDADE).map(ComandoListarAchados::severidade),
                argumentos.texto(OPCAO_REGRA),
                argumentos.texto(OPCAO_CHAVE).map(ChaveAcesso::new),
                argumentos.sinalizador(OPCAO_APENAS_ABERTOS),
                argumentos.inteiro(OPCAO_LIMITE, FiltroDeAchados.LIMITE_PADRAO));

        List<AchadoRegistrado> encontrados = consulta.listar(filtro);
        long total = consulta.contar(filtro);

        if (encontrados.isEmpty()) {
            saida.linha("Nenhum apontamento atende ao filtro.");
            return;
        }

        for (AchadoRegistrado registrado : encontrados) {
            imprimir(registrado);
            saida.linhaEmBranco();
        }
        saida.linha("Mostrando %d de %d apontamento(s).", encontrados.size(), total);
    }

    // Método auxiliar que mostra um apontamento, com evidências e tratativa.
    private void imprimir(AchadoRegistrado registrado) {
        Achado achado = registrado.achado();

        saida.linha("%s  [%s]  regra %s versão %s",
                registrado.id(), achado.severidade(), achado.regraId(), achado.regraVersao());
        saida.linha("  documento %s, item %d",
                achado.chaveAcesso().valor(), achado.numeroItem().orElse(0));
        saida.linha("  fundamento: %s", achado.fundamentoNormativo());
        saida.linha("  vigência aplicada: de %s até %s",
                achado.vigenciaAplicada().inicio(),
                achado.vigenciaAplicada().fim().map(Object::toString).orElse("sem fim declarado"));
        saida.linha("  valor em risco: %s",
                achado.quantiaEmRisco()
                        .map(quantia -> quantia.toPlainString())
                        .orElseGet(() -> "não calculável — "
                                + achado.valorEmRisco().motivoDaAusencia().orElse("")));

        for (Evidencia evidencia : achado.evidencias()) {
            saida.linha("  evidência: %s | encontrado: %s | esperado: %s",
                    evidencia.campoAnalisado(),
                    ausenteOu(evidencia.valorEncontrado()),
                    ausenteOu(evidencia.valorEsperado()));
        }

        registrado.tratativa().ifPresentOrElse(
                tratativa -> imprimirTratativa(tratativa),
                () -> saida.linha("  tratativa: em aberto"));
    }

    // Método auxiliar que mostra a decisão e a justificativa da tratativa.
    private void imprimirTratativa(Tratativa tratativa) {
        saida.linha("  tratativa: %s em %s", tratativa.decisao(), tratativa.registradoEm());
        saida.linha("    justificativa: %s", tratativa.justificativa());
    }

    // Método auxiliar que escreve (não informado) quando o valor falta, para não confundir com vazio ou zero.
    private static String ausenteOu(Optional<String> valor) {
        return valor.orElse("(não informado)");
    }

    // Método auxiliar que converte o texto em gravidade; recusa valor desconhecido.
    private static Severidade severidade(String informada) {
        return Arrays.stream(Severidade.values())
                .filter(severidade -> severidade.name().equalsIgnoreCase(informada))
                .findFirst()
                .orElseThrow(() -> new UsoInvalido(
                        "Severidade desconhecida: \"%s\". Valores aceitos: %s."
                                .formatted(informada, Arrays.toString(Severidade.values()))));
    }
}
