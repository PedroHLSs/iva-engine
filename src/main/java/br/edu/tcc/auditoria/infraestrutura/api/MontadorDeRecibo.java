package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ResultadoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDaToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeExecucoes;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Classe que monta o comprovante de uma análise, feita agora ou aberta do histórico. Os dois caminhos devolvem o mesmo tipo, para o comprovante não mudar quando for lido de novo.
// Emenda de 04/10/2026 (D019): o recibo traz quantos documentos repetidos, com o mesmo conteúdo, o lote descartou, e o texto de como foi a leitura diz isso quando houve algum.
// Emenda de 04/10/2026 (D023): o recibo traz a tolerância de valor da R05 que a execução usou, com a origem, ou o motivo de ela não ter sido registrada.
// Emenda de 04/10/2026 (D018): "Nenhum arquivo deixou de ser lido" só sai quando a leitura da execução foi registrada e a lista está vazia. Até essa data saía também quando a leitura não tinha sido registrada — toda execução do comando auditar —, porque a tabela vazia era lida como zero.
@Component
class MontadorDeRecibo {

    static final String CAMINHO_BASE = "/api/analises/";

    static final String MOTIVO_DA_LEITURA_NAO_REGISTRADA =
            "Esta execução não registrou quais arquivos deixaram de ser lidos: foi gravada antes de "
                    + "04/10/2026 pelo comando auditar, que só imprimia as falhas, ou por um caminho que "
                    + "não registra a leitura. Zero afirmaria que nenhum falhou, e isso não se sabe.";

    static final String MOTIVO_DOS_REPETIDOS_NAO_REGISTRADOS =
            "Esta execução não registrou quantos documentos repetidos o lote descartou: foi gravada antes "
                    + "de 04/10/2026, quando o mesmo documento repetido no lote era contado duas vezes, ou "
                    + "por um caminho que não registra a leitura.";

    private final ConsultaDeExecucoes execucoes;
    private final ConsultaDoAcervoDaAnalise acervo;
    private final ConsultaDaToleranciaDaExecucao tolerancias;

    // Construtor que recebe a consulta de execuções e a do acervo da análise.
    MontadorDeRecibo(ConsultaDeExecucoes execucoes, ConsultaDoAcervoDaAnalise acervo,
                     ConsultaDaToleranciaDaExecucao tolerancias) {
        this.execucoes = execucoes;
        this.acervo = acervo;
        this.tolerancias = tolerancias;
    }

    // Monta o comprovante da análise que acabou de rodar, com o que está em memória.
    ReciboDaAnalise de(ResultadoDaAnalise resultado) {
        return montar(
                resultado.auditoria().execucao(),
                Optional.of(resultado.arquivosIlegiveis()),
                Optional.of(resultado.auditoria().documentosRepetidosDescartados()),
                ToleranciaExposta.de(resultado.auditoria().tolerancia()));
    }

    // Monta o comprovante de uma análise gravada; recusa se ela não existir.
    ReciboDaAnalise porId(UUID id) {
        ExecucaoAuditoria execucao = execucoes.porId(id)
                .orElseThrow(() -> new ExecucaoNaoEncontrada(id));
        return montar(execucao, acervo.arquivosIlegiveis(id), acervo.documentosRepetidosDescartados(id),
                ToleranciaExposta.de(tolerancias.daExecucao(id)));
    }

    // Método auxiliar que monta o recibo com os dados da execução e os arquivos ilegíveis; sem a leitura registrada, contagem e lista saem nulas com o motivo.
    private static ReciboDaAnalise montar(
            ExecucaoAuditoria execucao,
            Optional<List<ArquivoIlegivel>> registrados,
            Optional<Integer> repetidos,
            ToleranciaExposta tolerancia) {

        Integer repetidosDescartados = repetidos.orElse(null);
        String motivoDosRepetidos = repetidos.isPresent() ? null : MOTIVO_DOS_REPETIDOS_NAO_REGISTRADOS;

        if (registrados.isEmpty()) {
            return new ReciboDaAnalise(
                    execucao.id().toString(),
                    CAMINHO_BASE + execucao.id(),
                    execucao.dataHora(),
                    execucao.versaoCatalogo(),
                    execucao.versaoConjuntoRegras(),
                    execucao.quantidadeDocumentos(),
                    execucao.quantidadeItens(),
                    null,
                    null,
                    MOTIVO_DA_LEITURA_NAO_REGISTRADA,
                    repetidosDescartados,
                    motivoDosRepetidos,
                    comoFoiALeituraNaoRegistrada(execucao),
                    tolerancia);
        }
        List<ArquivoIlegivel> ilegiveis = registrados.get();
        return new ReciboDaAnalise(
                execucao.id().toString(),
                CAMINHO_BASE + execucao.id(),
                execucao.dataHora(),
                execucao.versaoCatalogo(),
                execucao.versaoConjuntoRegras(),
                execucao.quantidadeDocumentos(),
                execucao.quantidadeItens(),
                ilegiveis.size(),
                ilegiveis.stream().map(ArquivoIlegivelExposto::de).toList(),
                null,
                repetidosDescartados,
                motivoDosRepetidos,
                comoFoiALeitura(execucao, ilegiveis.size()) + sobreOsRepetidos(repetidos),
                tolerancia);
    }

    // Método auxiliar que acrescenta ao texto da leitura as cópias descartadas, quando houve alguma.
    private static String sobreOsRepetidos(Optional<Integer> repetidos) {
        if (repetidos.isEmpty() || repetidos.get() == 0) {
            return "";
        }
        return (" %d arquivo(s) repetiam um documento já lido, com o mesmo conteúdo, e foram contados uma "
                + "vez só.").formatted(repetidos.get());
    }

    // Método auxiliar que escreve como foi a leitura quando ela não foi registrada: diz o que se sabe e o que não se sabe, nunca que nada falhou.
    private static String comoFoiALeituraNaoRegistrada(ExecucaoAuditoria execucao) {
        if (execucao.quantidadeDocumentos() == 0) {
            return "Nenhum documento fiscal foi lido. A leitura desta execução não foi registrada, então "
                    + "não se sabe se a origem estava vazia ou se os arquivos dela falharam.";
        }
        return ("%d documento(s) lido(s), com %d item(ns) ao todo. A leitura desta execução não foi "
                + "registrada: não se sabe se algum arquivo deixou de ser lido.")
                .formatted(execucao.quantidadeDocumentos(), execucao.quantidadeItens());
    }

    // Método auxiliar que escreve como foi a leitura, nos quatro casos possíveis. Escreve sempre, até quando nada falhou, para o campo nunca ficar em branco.
    private static String comoFoiALeitura(ExecucaoAuditoria execucao, int ilegiveis) {
        int documentos = execucao.quantidadeDocumentos();

        if (documentos == 0 && ilegiveis > 0) {
            return ("Nenhum documento pôde ser lido: os %d arquivo(s) enviados falharam. A análise "
                    + "foi registrada assim mesmo, para que a tentativa e os motivos de cada falha "
                    + "fiquem gravados.").formatted(ilegiveis);
        }
        if (documentos == 0) {
            return "Nenhum documento fiscal foi encontrado na origem, e nenhum arquivo falhou ao ser "
                    + "lido: não havia o que analisar.";
        }
        if (ilegiveis == 0) {
            return ("%d documento(s) lido(s), com %d item(ns) ao todo. Nenhum arquivo deixou de ser "
                    + "lido.").formatted(documentos, execucao.quantidadeItens());
        }
        return ("%d documento(s) lido(s), com %d item(ns) ao todo. %d arquivo(s) não puderam ser "
                + "lidos e aparecem listados à parte — eles não entram em contagem nenhuma de "
                + "conferência, porque não chegaram a ser documentos.")
                .formatted(documentos, execucao.quantidadeItens(), ilegiveis);
    }
}
