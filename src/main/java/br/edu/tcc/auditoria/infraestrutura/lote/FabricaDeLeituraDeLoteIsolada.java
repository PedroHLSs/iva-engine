package br.edu.tcc.auditoria.infraestrutura.lote;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.DescricaoDoProduto;
import br.edu.tcc.auditoria.aplicacao.analise.FabricaDeLeituraDeLote;
import br.edu.tcc.auditoria.aplicacao.analise.LeituraDeLote;
import br.edu.tcc.auditoria.aplicacao.auditoria.FonteDeLoteDeDocumentos;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.infraestrutura.xml.DescricoesDeProdutoEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.FalhasDeLeituraEmMemoria;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorDocumentoFiscal;
import br.edu.tcc.auditoria.infraestrutura.xml.LeitorLote;
import br.edu.tcc.auditoria.infraestrutura.xml.NormalizadorDocumento;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monta, a cada análise, uma cadeia de leitura com registro de falhas próprio.
 *
 * <p>Os objetos caros continuam sendo os mesmos de sempre: o leitor de XML e o
 * normalizador são injetados e compartilhados, como na CLI. O que nasce novo a
 * cada chamada são as três peças baratas que carregam estado de lote — o
 * registro de falhas, o leitor de lote que escreve nele, e a fonte que os
 * junta.</p>
 *
 * <p>Nenhuma linha da Etapa 4 muda por causa disto. {@code LeitorLote} e
 * {@code FonteDeLoteNoSistemaDeArquivos} já recebiam essas dependências por
 * construtor; a novidade é só chamar o construtor mais de uma vez.</p>
 */
@Component
class FabricaDeLeituraDeLoteIsolada implements FabricaDeLeituraDeLote {

    private final LeitorDocumentoFiscal leitor;
    private final NormalizadorDocumento normalizador;

    FabricaDeLeituraDeLoteIsolada(LeitorDocumentoFiscal leitor, NormalizadorDocumento normalizador) {
        this.leitor = leitor;
        this.normalizador = normalizador;
    }

    @Override
    public LeituraDeLote nova() {
        FalhasDeLeituraEmMemoria falhas = new FalhasDeLeituraEmMemoria();
        DescricoesDeProdutoEmMemoria descricoes = new DescricoesDeProdutoEmMemoria();
        FonteDeLoteNoSistemaDeArquivos fonte = new FonteDeLoteNoSistemaDeArquivos(
                new LeitorLote(leitor, normalizador, falhas, descricoes), falhas);
        return new LeituraIsolada(fonte, falhas, descricoes);
    }

    /**
     * Uma leitura e as falhas dela.
     *
     * <p>A conversão para {@link ArquivoIlegivel} passa por
     * {@link OrigemDeArquivoIlegivel}, que é onde o caminho perde o diretório de
     * quem rodou e o CNPJ que viaja dentro do nome do arquivo.</p>
     */
    private record LeituraIsolada(
            FonteDeLoteNoSistemaDeArquivos origem,
            FalhasDeLeituraEmMemoria falhas,
            DescricoesDeProdutoEmMemoria descricoes) implements LeituraDeLote {

        @Override
        public FonteDeLoteDeDocumentos fonte() {
            return origem;
        }

        @Override
        public List<ArquivoIlegivel> arquivosIlegiveis() {
            return falhas.falhas().stream().map(OrigemDeArquivoIlegivel::de).toList();
        }

        /*
         * Os dois níveis de Optional viram os três estados de DescricaoDoProduto,
         * e é aqui que a tradução acontece: sem entrada, a leitura não viu o item;
         * com entrada vazia, viu e o documento nada declarou; com texto, é o texto.
         */
        @Override
        public DescricaoDoProduto descricaoDe(HashDoItem hashDoItem) {
            return descricoes.de(hashDoItem)
                    .map(DescricaoDoProduto::de)
                    .orElseGet(DescricaoDoProduto::naoLidaNestaAnalise);
        }
    }
}
