package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.analise.RegistroDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.infraestrutura.lote.FonteDeLoteNoSistemaDeArquivos;
import br.edu.tcc.auditoria.infraestrutura.lote.OrigemDeArquivoIlegivel;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Classe do comando auditar, que audita uma pasta ou um .zip de documentos e mostra o resumo: achados por gravidade e por regra, não avaliadas e arquivos que não foram lidos, para zero apontamento não parecer lote limpo.
// Emenda de 04/10/2026 (D019): mostra e registra quantos documentos repetidos, com o mesmo conteúdo, o lote descartou. Arquivos com a mesma chave e conteúdo diferente saem na lista dos que ficaram de fora.
// Emenda de 04/10/2026 (D018): os arquivos que não foram lidos passaram a ser gravados junto da execução, pelo mesmo registro da análise da interface, sem a pasta e sem o CNPJ no nome. Até essa data eram só impressos, e a API e a planilha liam a tabela vazia como "nenhum falhou". Os itens lidos não são gravados aqui: a lista de itens vai vazia, como sempre foi para a CLI.
@Component
class ComandoAuditar implements Comando {

    static final String NOME = "auditar";

    private static final String OPCAO_ORIGEM = "origem";

    private final ServicoDeAuditoria servico;
    private final FonteDeLoteNoSistemaDeArquivos fonte;
    private final RegistroDoAcervoDaAnalise acervo;
    private final Saida saida;

    // Construtor que recebe o serviço de auditoria, a fonte de lote, o registro do acervo e a saída.
    ComandoAuditar(
            ServicoDeAuditoria servico,
            FonteDeLoteNoSistemaDeArquivos fonte,
            RegistroDoAcervoDaAnalise acervo,
            Saida saida) {
        this.servico = servico;
        this.fonte = fonte;
        this.acervo = acervo;
        this.saida = saida;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Audita os documentos de um diretório ou de um pacote ZIP.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --origem=<caminho>

                  --origem  diretório com arquivos .xml, ou um arquivo .zip que os
                            contenha. Subdiretórios são percorridos.
                """.formatted(NOME);
    }

    // Audita a origem informada em --origem e mostra o resumo.
    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_ORIGEM));
        Path origem = argumentos.caminhoObrigatorio(OPCAO_ORIGEM);

        // O registro de falhas da fonte é do processo: o que já estava nele antes desta auditoria não é dela.
        int falhasAnteriores = fonte.falhasDeLeitura().size();
        ResultadoDaAuditoria resultado = servico.auditar(origem);
        List<FalhaDeLeitura> todas = fonte.falhasDeLeitura();
        List<FalhaDeLeitura> destaAuditoria = todas.subList(falhasAnteriores, todas.size());

        acervo.registrar(
                resultado.execucao().id(),
                List.of(),
                destaAuditoria.stream().map(OrigemDeArquivoIlegivel::de).toList(),
                resultado.documentosRepetidosDescartados());
        imprimir(resultado, destaAuditoria);
    }

    // Método auxiliar que mostra a execução, os achados, as avaliações e os arquivos não lidos.
    private void imprimir(ResultadoDaAuditoria resultado, List<FalhaDeLeitura> falhas) {
        ExecucaoAuditoria execucao = resultado.execucao();

        saida.linha("Execução %s", execucao.id());
        saida.linha("  em ................. %s", execucao.dataHora());
        saida.linha("  entrada ............ %s", execucao.hashEntrada());
        saida.linha("  catálogo ........... %s", execucao.versaoCatalogo());
        saida.linha("  conjunto de regras . %s", execucao.versaoConjuntoRegras());
        // D023 (04/10/2026): a tolerância da R05 usada, com a origem; o padrão aparece escrito como padrão.
        saida.linha("  tolerância R05 ..... %s", resultado.tolerancia()
                .map(ToleranciaDaExecucao::texto).orElse(ToleranciaDaExecucao.NAO_REGISTRADA));
        saida.linha("  documentos ......... %d", execucao.quantidadeDocumentos());
        saida.linha("  itens .............. %d", execucao.quantidadeItens());
        saida.linha("  repetidos descartados . %d (mesmo conteúdo de outro arquivo do lote, contados uma vez)",
                resultado.documentosRepetidosDescartados());
        saida.linhaEmBranco();

        saida.linha("Apontamentos por severidade");
        for (Severidade severidade : execucao.severidadesContadas()) {
            saida.linha("  %-12s %d", severidade, execucao.achadosDe(severidade));
        }
        saida.linhaEmBranco();

        saida.linha("Apontamentos por regra");
        for (Map.Entry<String, Integer> porRegra : execucao.achadosPorRegra().entrySet()) {
            saida.linha("  %-12s %d", porRegra.getKey(), porRegra.getValue());
        }
        saida.linhaEmBranco();

        saida.linha("Avaliações: %d, sendo %d conformes, %d com apontamento e %d não avaliadas.",
                resultado.quantidadeDeAvaliacoes(),
                resultado.quantidadeDeConformes(),
                resultado.achados().size(),
                resultado.quantidadeDeNaoAvaliadas());

        if (falhas.isEmpty()) {
            saida.linha("Nenhum arquivo deixou de ser lido.");
            return;
        }
        saida.linhaEmBranco();
        saida.linha("%d arquivo(s) não puderam ser lidos e ficaram de fora da auditoria:",
                falhas.size());
        for (FalhaDeLeitura falha : falhas) {
            saida.linha("  %s: %s (%s)", falha.origem(), falha.motivo(), falha.tipoDeErro());
        }
    }
}
