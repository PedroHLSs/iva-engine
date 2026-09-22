package br.edu.tcc.auditoria.aplicacao.analise;

import br.edu.tcc.auditoria.aplicacao.auditoria.DocumentoComItens;
import br.edu.tcc.auditoria.aplicacao.auditoria.MotorAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ProvedorDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.auditoria.RepositorioDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.regras.ToleranciaDeValor;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma análise: o mesmo pipeline da auditoria, mais o registro do que ela leu.
 *
 * <h2>Não há caminho paralelo de processamento, e isso é a regra da etapa</h2>
 *
 * <p>Este serviço <strong>não lê XML, não normaliza, não pseudonimiza, não monta
 * contexto normativo, não aplica regra e não grava apontamento.</strong> Tudo
 * isso continua sendo {@link ServicoDeAuditoria}, exatamente o mesmo objeto que
 * o comando {@code auditar} usa, com os mesmos colaboradores. O que este serviço
 * acrescenta são duas coisas que a CLI não precisava:</p>
 *
 * <ol>
 *   <li>uma leitura <em>isolada</em>, para que os arquivos ilegíveis de uma
 *       análise não vazem para a seguinte — ver {@link LeituraDeLote};</li>
 *   <li>o registro do acervo: quais itens esta análise leu e quais arquivos ela
 *       não conseguiu ler, que é o que permite reabrir o resultado depois.</li>
 * </ol>
 *
 * <p>O {@link ServicoDeAuditoria} é construído por análise, e não injetado, por
 * causa do item 1: ele guarda a fonte de lote no construtor, e a fonte é o que
 * precisa ser nova a cada vez. É objeto barato — seis referências —, e os
 * colaboradores caros continuam sendo os mesmos de sempre.</p>
 *
 * <h2>Ler nada é um resultado</h2>
 *
 * <p>Um pacote em que todos os arquivos falharam produz execução gravada, zero
 * documentos, zero itens e a lista de ilegíveis cheia. Isso não é erro e não
 * vira exceção: virar exceção apagaria o registro de que houve tentativa, e a
 * pessoa ficaria sem saber quantos arquivos ela mandou nem por que nenhum
 * passou.</p>
 */
public final class ServicoDeAnalise {

    private final FabricaDeLeituraDeLote leituras;
    private final ProvedorDeCatalogo provedorDeCatalogo;
    private final RepositorioDaAuditoria repositorio;
    private final MotorAuditoria motor;
    private final ToleranciaDeValor tolerancia;
    private final Clock relogio;
    private final RegistroDoAcervoDaAnalise acervo;

    public ServicoDeAnalise(
            FabricaDeLeituraDeLote leituras,
            ProvedorDeCatalogo provedorDeCatalogo,
            RepositorioDaAuditoria repositorio,
            MotorAuditoria motor,
            ToleranciaDeValor tolerancia,
            Clock relogio,
            RegistroDoAcervoDaAnalise acervo) {

        this.leituras = exigir(leituras, "a fábrica de leitura de lote");
        this.provedorDeCatalogo = exigir(provedorDeCatalogo, "o provedor de catálogo");
        this.repositorio = exigir(repositorio, "o repositório da auditoria");
        this.motor = exigir(motor, "o motor de auditoria");
        this.tolerancia = exigir(tolerancia, "a tolerância de valor");
        this.relogio = exigir(relogio, "o relógio");
        this.acervo = exigir(acervo, "o registro do acervo da análise");
    }

    /**
     * Analisa o que estiver na origem — um diretório de XML ou um pacote.
     *
     * <p>A origem já chega pronta: quem recebeu o arquivo pela web é que a
     * materializou e conferiu. Este serviço não sabe de upload, de limite de
     * tamanho nem de pacote.</p>
     */
    public ResultadoDaAnalise analisar(Path origem) {
        if (origem == null) {
            throw new AnaliseInvalida("Não há origem a analisar.");
        }

        LeituraDeLote leitura = leituras.nova();
        if (leitura == null) {
            throw new AnaliseInvalida("A fábrica não devolveu leitura de lote.");
        }

        ServicoDeAuditoria auditoria = new ServicoDeAuditoria(
                leitura.fonte(), provedorDeCatalogo, repositorio, motor, tolerancia, relogio);

        ResultadoDaAuditoria resultado = auditoria.auditar(origem);
        List<ArquivoIlegivel> ilegiveis = leitura.arquivosIlegiveis();

        acervo.registrar(resultado.execucao().id(), itensLidos(resultado, leitura), ilegiveis);

        return new ResultadoDaAnalise(resultado, ilegiveis);
    }

    /**
     * Todos os itens lidos, com o resumo do conteúdo que esta análise viu.
     *
     * <p>Inclusive os que não produziram apontamento nem pendência — são
     * justamente eles que se perderiam se a lista fosse derivada dos
     * apontamentos.</p>
     *
     * <p>A descrição do produto vem da mesma leitura que produziu os itens, e não
     * de uma segunda passada pelo XML. Ler de novo abriria a possibilidade de as
     * duas leituras discordarem, o que é pior que não ter a descrição.</p>
     */
    private static List<ItemDaAnalise> itensLidos(
            ResultadoDaAuditoria resultado, LeituraDeLote leitura) {

        List<ItemDaAnalise> itens = new ArrayList<>();
        for (DocumentoComItens documento : resultado.documentos()) {
            for (ItemDocumento item : documento.itensOrdenados()) {
                // O mesmo cálculo que a leitura usou para endereçar a descrição:
                // mesma função, mesmas entradas. É isso que torna impossível
                // associar a descrição ao produto errado.
                HashDoItem resumo = HashDoItem.de(documento.documento().chaveAcesso(), item);
                itens.add(new ItemDaAnalise(
                        documento.documento().chaveAcesso(),
                        item.numeroItem(),
                        resumo,
                        leitura.descricaoDe(resumo)));
            }
        }
        return List.copyOf(itens);
    }

    private static <T> T exigir(T valor, String oQueFalta) {
        if (valor == null) {
            throw new AnaliseInvalida("O serviço de análise precisa de %s.".formatted(oQueFalta));
        }
        return valor;
    }
}
