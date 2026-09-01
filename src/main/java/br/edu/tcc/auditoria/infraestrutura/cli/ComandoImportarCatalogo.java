package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Comando {@code importar-catalogo}: carrega as tabelas normativas de arquivos
 * CSV fornecidos pelo usuário.
 *
 * <p>É o único caminho pelo qual conteúdo normativo entra no sistema. Nenhuma
 * migration insere alíquota, código ou vigência; o repositório não guarda nada
 * disso em código.</p>
 */
@Component
class ComandoImportarCatalogo implements Comando {

    static final String NOME = "importar-catalogo";

    private static final String OPCAO_DIRETORIO = "diretorio";
    private static final String OPCAO_VERSAO = "versao";
    private static final DateTimeFormatter FORMATO_DA_VERSAO_AUTOMATICA =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ServicoDeImportacaoDeCatalogo servico;
    private final Saida saida;
    private final Clock relogio;

    ComandoImportarCatalogo(ServicoDeImportacaoDeCatalogo servico, Saida saida, Clock relogio) {
        this.servico = servico;
        this.saida = saida;
        this.relogio = relogio;
    }

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public String descricao() {
        return "Importa as tabelas normativas de um diretório de arquivos CSV.";
    }

    @Override
    public String modoDeUsar() {
        return """
                %s --diretorio=<caminho> [--versao=<nome>]

                  --diretorio  diretório com os arquivos CSV do catálogo:
                               %s
                  --versao     nome desta carga, registrado em cada execução de
                               auditoria feita contra ela. Se omitido, é gerado a
                               partir da data e hora.
                """.formatted(NOME, LeitorDeCatalogoEmCsv.arquivosEsperados());
    }

    @Override
    public void executar(Argumentos argumentos) {
        argumentos.exigirSomente(List.of(OPCAO_DIRETORIO, OPCAO_VERSAO));

        Path diretorio = argumentos.caminhoObrigatorio(OPCAO_DIRETORIO);
        String versao = argumentos.texto(OPCAO_VERSAO).orElseGet(this::versaoAutomatica);

        CargaDeCatalogo carga;
        try {
            carga = LeitorDeCatalogoEmCsv.ler(diretorio, versao);
        } catch (IOException erroDeLeitura) {
            throw new UncheckedIOException(
                    "Não foi possível ler o catálogo em \"%s\".".formatted(diretorio), erroDeLeitura);
        }

        ResumoDaImportacao resumo = servico.importar(carga);

        saida.linha("Catálogo importado como versão \"%s\".", resumo.versao());
        saida.linha("  classificações tributárias: %d", resumo.classificacoesTributarias());
        saida.linha("  NCM: %d", resumo.registrosDeNcm());
        saida.linha("  itens de anexo: %d", resumo.itensDeAnexo());
        saida.linha("  alíquotas: %d", resumo.aliquotas());
        saida.linha("  total: %d registros", resumo.total());
    }

    private String versaoAutomatica() {
        return "carga-" + FORMATO_DA_VERSAO_AUTOMATICA.format(relogio.instant());
    }
}
