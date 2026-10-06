package br.edu.tcc.auditoria.infraestrutura.cli;

import br.edu.tcc.auditoria.aplicacao.catalogo.AcervoDeCargasEmMemoriaParaTeste;
import br.edu.tcc.auditoria.aplicacao.catalogo.CargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.RepositorioDeCargaDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeCargas;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo;
import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.infraestrutura.catalogo.LeitorDeCatalogoEmCsv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Correção de 04/10/2026 (D025): o resumo da importação conta os anexos declarados, que são a quinta tabela da carga desde a revisão de 30/09 a 02/10/2026. Até essa data a CLI e a resposta do POST /api/cargas listavam quatro tabelas, e o total não os somava. Valores fictícios.
class ResumoComAnexosDeclaradosTest {

    private static final String VIGENCIA = "1900-01-01;1900-12-31";

    @TempDir
    Path diretorio;

    private final List<String> linhas = new ArrayList<>();

    @BeforeEach
    void escreverCatalogo() throws IOException {
        escrever("cobertura.csv", """
                tabela;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CLASSIFICACAO_TRIBUTARIA;%1$s;FONTE FICTICIA v0.0;FICTICIO
                NCM;%1$s;FONTE FICTICIA v0.0;FICTICIO
                ITEM_ANEXO;%1$s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("classificacao-tributaria.csv", """
                codigo;cstsCompativeis;dispositivoLegal;indicadorDeBeneficio;percentualReducao;\
                camposObrigatoriosCondicionados;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                XXX000;AAA;Dispositivo ficticio;false;;NENHUM;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("registro-ncm.csv", """
                ncm;descricao;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;Descricao ficticia;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("item-anexo.csv", """
                ncm;identificadorDoAnexo;tipoDeTratamento;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                00000000;ANEXO-XX;TRATAMENTO-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("aliquota-vigente.csv", """
                tributo;percentual;abrangencia;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                CBS;99,99;ABRANGENCIA-XX;%s;FONTE FICTICIA v0.0;FICTICIO
                """.formatted(VIGENCIA));
        escrever("anexos-declarados.csv", """
                identificadorDoAnexo;tipoDeCodigo;vigenciaInicio;vigenciaFim;fonteNormativa;natureza
                ANEXO-XX;NCM;1900-01-01;;FONTE FICTICIA v0.0;FICTICIO
                ANEXO-YY;NBS;;;FONTE FICTICIA v0.0;FICTICIO
                """);
    }

    @Test
    void oResumoDeveContarOsAnexosDeclaradosENoTotal() throws IOException {
        ResumoDaImportacao resumo = servico().importar(LeitorDeCatalogoEmCsv.ler(diretorio, "carga-ficticia"));

        assertThat(resumo.anexosDeclarados()).isEqualTo(2);
        assertThat(resumo.total()).isEqualTo(1 + 1 + 1 + 1 + 2);
    }

    @Test
    void aCliDeveDizerQuantosAnexosDeclaradosEntraram() {
        // D026 (04/10/2026): o comando grava pelo ServicoDeCargas, dentro da trava do acervo; até essa data recebia o serviço de importação direto.
        new ComandoImportarCatalogo(new ServicoDeCargas(new AcervoDeCargasEmMemoriaParaTeste(), servico()), linhas::add,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
                .executar(Argumentos.de("importar-catalogo", "--diretorio=" + diretorio, "--versao=carga-ficticia"));

        assertThat(linhas).anyMatch(linha -> linha.strip().equals("anexos declarados: 2"));
        assertThat(linhas).anyMatch(linha -> linha.strip().equals("total: 6 registros"));
    }

    private static ServicoDeImportacaoDeCatalogo servico() {
        return new ServicoDeImportacaoDeCatalogo(new RepositorioDeCargaDeCatalogo() {
            @Override
            public void salvar(CargaDeCatalogo carga) {
            }

            @Override
            public Optional<String> versaoDaCargaMaisRecente() {
                return Optional.empty();
            }
        });
    }

    private void escrever(String nome, String conteudo) throws IOException {
        Files.writeString(diretorio.resolve(nome), conteudo, StandardCharsets.UTF_8);
    }
}
