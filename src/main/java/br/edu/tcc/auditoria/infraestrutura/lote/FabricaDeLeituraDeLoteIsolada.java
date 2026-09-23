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

// Classe que cria, a cada análise, uma leitura com registro de falhas próprio, para os ilegíveis de uma análise não aparecerem na seguinte. O leitor de XML e o normalizador são os mesmos de sempre; só as peças que guardam estado do lote nascem novas.
@Component
class FabricaDeLeituraDeLoteIsolada implements FabricaDeLeituraDeLote {

    private final LeitorDocumentoFiscal leitor;
    private final NormalizadorDocumento normalizador;

    // Construtor que recebe o leitor de XML e o normalizador, compartilhados entre as análises.
    FabricaDeLeituraDeLoteIsolada(LeitorDocumentoFiscal leitor, NormalizadorDocumento normalizador) {
        this.leitor = leitor;
        this.normalizador = normalizador;
    }

    // Cria uma leitura nova, com registro de falhas e de descrições só dela.
    @Override
    public LeituraDeLote nova() {
        FalhasDeLeituraEmMemoria falhas = new FalhasDeLeituraEmMemoria();
        DescricoesDeProdutoEmMemoria descricoes = new DescricoesDeProdutoEmMemoria();
        FonteDeLoteNoSistemaDeArquivos fonte = new FonteDeLoteNoSistemaDeArquivos(
                new LeitorLote(leitor, normalizador, falhas, descricoes), falhas);
        return new LeituraIsolada(fonte, falhas, descricoes);
    }

    // Representa uma leitura e as falhas dela. O nome do arquivo ilegível passa por OrigemDeArquivoIlegivel, que tira a pasta e o CNPJ.
    private record LeituraIsolada(
            FonteDeLoteNoSistemaDeArquivos origem,
            FalhasDeLeituraEmMemoria falhas,
            DescricoesDeProdutoEmMemoria descricoes) implements LeituraDeLote {

        @Override
        public FonteDeLoteDeDocumentos fonte() {
            return origem;
        }

        // Devolve os arquivos que não puderam ser lidos, já sem pasta e sem CNPJ no nome.
        @Override
        public List<ArquivoIlegivel> arquivosIlegiveis() {
            return falhas.falhas().stream().map(OrigemDeArquivoIlegivel::de).toList();
        }

        // Devolve a descrição do produto nos três casos: item não visto nesta leitura, item sem descrição na nota, ou o texto.
        @Override
        public DescricaoDoProduto descricaoDe(HashDoItem hashDoItem) {
            return descricoes.de(hashDoItem)
                    .map(DescricaoDoProduto::de)
                    .orElseGet(DescricaoDoProduto::naoLidaNestaAnalise);
        }
    }
}
