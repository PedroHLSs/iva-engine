package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.auditoria.ResultadoDaAuditoria;
import br.edu.tcc.auditoria.aplicacao.auditoria.ServicoDeAuditoria;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;
import br.edu.tcc.auditoria.infraestrutura.lote.FonteDeLoteNoSistemaDeArquivos;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhaDeLeitura;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Comando {@code auditar}: audita um diretório ou um pacote ZIP de documentos.
 *
 * <p>O resumo impresso ao final traz, junto: apontamentos por severidade,
 * apontamentos por regra, avaliações que não concluíram e arquivos que não
 * puderam ser lidos. Os quatro números são necessários para interpretar o
 * relatório — um lote com zero apontamentos e milhares de avaliações não
 * concluídas não é um lote limpo, e o resumo não pode deixar parecer que é.</p>
 */
@Component
class ComandoAuditar implements Comando {

    static final String NOME = "auditar";

    private static final String OPCAO_ORIGEM = "origem";

    private final ServicoDeAuditoria servico;
    private final FonteDeLoteNoSistemaDeArquivos fonte;
    private final Saida saida;

    ComandoAuditar(ServicoDeAuditoria servico, FonteDeLoteNoSistemaDeArquivos fonte, Saida saida) {
        this.servico = servico;
        this.fonte = fonte;
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

    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_ORIGEM));
        Path origem = argumentos.caminhoObrigatorio(OPCAO_ORIGEM);

        ResultadoDaAuditoria resultado = servico.auditar(origem);
        imprimir(resultado);
    }

    private void imprimir(ResultadoDaAuditoria resultado) {
        ExecucaoAuditoria execucao = resultado.execucao();

        saida.linha("Execução %s", execucao.id());
        saida.linha("  em ................. %s", execucao.dataHora());
        saida.linha("  entrada ............ %s", execucao.hashEntrada());
        saida.linha("  catálogo ........... %s", execucao.versaoCatalogo());
        saida.linha("  conjunto de regras . %s", execucao.versaoConjuntoRegras());
        saida.linha("  documentos ......... %d", execucao.quantidadeDocumentos());
        saida.linha("  itens .............. %d", execucao.quantidadeItens());
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

        List<FalhaDeLeitura> falhas = fonte.falhasDeLeitura();
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
